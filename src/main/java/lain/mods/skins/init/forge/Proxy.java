package lain.mods.skins.init.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import lain.mods.skins.api.SkinProviderAPI;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.impl.ConfigOptions;
import lain.mods.skins.impl.PlayerProfile;
import lain.mods.skins.impl.forge.CustomSkinTexture;
import lain.mods.skins.impl.forge.ImageUtils;
import lain.mods.skins.providers.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

enum Proxy {

    INSTANCE;

    Map<ByteBuffer, CustomSkinTexture> textures = new WeakHashMap<>();

    ResourceLocation generateRandomLocation() {
        return new ResourceLocation("offlineskins", String.format("textures/generated/%s", UUID.randomUUID().toString()));
    }

    ResourceLocation getLocationCape(GameProfile profile) {
        ISkin skin = SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(profile));
        if (skin != null && skin.isDataReady())
            return getOrCreateTexture(skin.getData(), skin).getLocation();
        return null;
    }

    ResourceLocation getLocationSkin(GameProfile profile) {
        ISkin skin = SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(profile));
        if (skin != null && skin.isDataReady())
            return getOrCreateTexture(skin.getData(), skin).getLocation();
        return null;
    }

    CustomSkinTexture getOrCreateTexture(ByteBuffer data, ISkin skin) {
        if (!textures.containsKey(data)) {
            CustomSkinTexture texture = new CustomSkinTexture(generateRandomLocation(), data);
            Minecraft.getInstance().getTextureManager().loadTexture(texture.getLocation(), texture);
            textures.put(data, texture);

            if (skin != null) {
                skin.setRemovalListener(s -> {
                    if (data == s.getData()) {
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().getTextureManager().deleteTexture(texture.getLocation());
                            textures.remove(data);
                        });
                    }
                });
            }
        }
        return textures.get(data);
    }

    String getSkinType(GameProfile profile) {
        ResourceLocation location = getLocationSkin(profile);
        if (location != null) {
            ISkin skin = SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(profile));
            if (skin != null && skin.isDataReady())
                return skin.getSkinType();
        }
        return null;
    }

    void handleClientTickEvent(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            World world = Minecraft.getInstance().world;
            if (world != null) {
                for (PlayerEntity player : world.getPlayers()) {
                    SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(player.getGameProfile()));
                    SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(player.getGameProfile()));
                }
            }
        }
    }

    void init() {
        Logger logger = LogManager.getLogger(ForgeOfflineSkins.class);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path pathToConfig = Paths.get(".", "config", "offlineskins.json");
        pathToConfig.toFile().getParentFile().mkdirs();
        if (!pathToConfig.toFile().exists()) {
            try (Writer w = Files.newBufferedWriter(pathToConfig, StandardCharsets.UTF_8)) {
                gson.toJson(new ConfigOptions().defaultOptions(), w);
            } catch (Throwable t) {
                logger.error("[OfflineSkins] Failed to write default config file.", t);
            }
        }
        ConfigOptions config = null;
        try {
            config = gson.fromJson(Files.lines(pathToConfig, StandardCharsets.UTF_8).collect(Collectors.joining(System.getProperty("line.separator"))), ConfigOptions.class);
        } catch (Throwable t) {
            logger.error("[OfflineSkins] Failed to read config file.", t);
            config = new ConfigOptions();
        }
        config.validate();

        SkinProviderAPI.SKIN.clearProviders();
        SkinProviderAPI.SKIN.registerProvider(new UserManagedSkinProvider(Paths.get(".", "cachedImages")).withFilter(ImageUtils::legacyFilter));
        if (config.useCustomServer)
            SkinProviderAPI.SKIN.registerProvider(new CustomServerSkinProvider().setHost(config.hostCustomServer).withFilter(ImageUtils::legacyFilter));
        if (config.useCustomServer2)
            SkinProviderAPI.SKIN.registerProvider(new CustomServerSkinProvider2().setHost(config.hostCustomServer2Skin).withFilter(ImageUtils::legacyFilter));
        if (config.useMojang)
            SkinProviderAPI.SKIN.registerProvider(new MojangSkinProvider().withFilter(ImageUtils::legacyFilter));
        if (config.useCrafatar)
            SkinProviderAPI.SKIN.registerProvider(new CrafatarSkinProvider().withFilter(ImageUtils::legacyFilter));

        SkinProviderAPI.CAPE.clearProviders();
        SkinProviderAPI.CAPE.registerProvider(new UserManagedCapeProvider(Paths.get(".", "cachedImages")));
        if (config.useCustomServer)
            SkinProviderAPI.CAPE.registerProvider(new CustomServerCapeProvider().setHost(config.hostCustomServer));
        if (config.useCustomServer2)
            SkinProviderAPI.CAPE.registerProvider(new CustomServerCapeProvider2().setHost(config.hostCustomServer2Cape));
        if (config.useMojang)
            SkinProviderAPI.CAPE.registerProvider(new MojangCapeProvider());
        if (config.useCrafatar)
            SkinProviderAPI.CAPE.registerProvider(new CrafatarCapeProvider());

        MinecraftForge.EVENT_BUS.addListener(this::handleClientTickEvent);
    }

}
