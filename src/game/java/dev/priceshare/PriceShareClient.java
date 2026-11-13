package dev.priceshare;

import dev.priceshare.core.Log;
import dev.priceshare.core.PriceShare;
import dev.priceshare.core.Uploader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import com.mojang.authlib.exceptions.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PriceShareClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Price Share");

    private static final Pattern STATUS = Pattern.compile("Status: (\\d{3})");

    /** The HTTP status in an authlib error ("Status: 429"), or -1. */
    static int statusOf(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            Matcher m = STATUS.matcher(String.valueOf(t.getMessage()));
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    @Override
    public void onInitializeClient() {
        Log.info = LOG::info;
        Log.warn = LOG::warn;
        FabricLoader loader = FabricLoader.getInstance();
        String mod = loader.getModContainer("priceshare").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String mc = loader.getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        PriceShare.init(loader.getConfigDir().resolve("priceshare"), mod, mc, new Uploader.Session() {
            @Override
            public String name() {
                return Minecraft.getInstance().getUser().getName();
            }

            @Override
            public String uuid() {
                return Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");
            }

            @Override
            public void join(String serverId) throws Exception {
                Minecraft mc = Minecraft.getInstance();
                User user = mc.getUser();
                try {
                    mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
                } catch (AuthenticationException e) {
                    // Only the status goes on: the message can hold a whole HTML error page.
                    throw new Uploader.JoinException(statusOf(e));
                }
            }
        });
    }
}
