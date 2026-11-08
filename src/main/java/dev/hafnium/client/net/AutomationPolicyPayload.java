package dev.hafnium.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Optional server → client message: the automation capabilities this server allows (by id, see
 * {@link dev.hafnium.client.core.permission.Capability#id()}). It is a complete allow-list: unlisted capabilities are
 * switched off on that server, and an empty list switches off all of them. It is needed only to grant
 * {@code SERVER_GRANT} capabilities (player combat); servers that never send it still allow ordinary player-input
 * automation and all client-local features.
 */
public record AutomationPolicyPayload(int version, List<String> capabilities) implements CustomPacketPayload {
    public static final int VERSION = 1;
    public static final Type<AutomationPolicyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("hafnium", "automation_policy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutomationPolicyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AutomationPolicyPayload::version,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), AutomationPolicyPayload::capabilities,
            AutomationPolicyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
