package tokyo.nikokingames.antipacketflood.bukkit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.core.helpers.Charsets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.StreamSerializer;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;

import io.netty.buffer.ByteBuf;

public class fixerBukkitMain extends JavaPlugin{
	
	private static final  Map<Player, Long> PACKET_USAGE = new ConcurrentHashMap<>();
	
	private String Command, KickMessage;
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		
		Command = getConfig().getString("Command");
		KickMessage = getConfig().getString("KickMessage");
		

	        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.Play.Client.CUSTOM_PAYLOAD) {
	            @Override
	            public void onPacketReceiving(PacketEvent event) {
	                checkPacket(event);
	            }
	        });

	        Bukkit.getScheduler().runTaskTimer(this, () -> {
	            for (Iterator<Map.Entry<Player, Long>> iterator = PACKET_USAGE.entrySet().iterator(); iterator.hasNext(); ) {
	                Player player = iterator.next().getKey();
	                if (!player.isOnline() || !player.isValid())
	                    iterator.remove();
	            }
	        }, 20L, 20L);
	    }

	    @Override
	    public void onDisable() {
	        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
	    }

	    private void checkPacket(PacketEvent event) {
	        Player player = event.getPlayer();
	        long lastPacket = PACKET_USAGE.getOrDefault(player, -1L);

	        if (lastPacket == -2L) {
	            event.setCancelled(true);
	            return;
	        }

	        String name = event.getPacket().getStrings().readSafely(0);
	        if (!"MC|BSign".equals(name) && !"MC|BEdit".equals(name) && !"REGISTER".equals(name))
	            return;

	        try {
	            if ("REGISTER".equals(name)) {
	                checkChannels(event);
	            } else {
	                if (elapsed(lastPacket, 100L)) {
	                    PACKET_USAGE.put(player, System.currentTimeMillis());
	                } else {
	                    throw new IOException("Packet flood");
	                }

	                checkNbtTags(event);
	            }
	        } catch (Throwable ex) {
	            // Set last packet usage to -2 so we wouldn't mind checking him again
	            PACKET_USAGE.put(player, -2L);

	            Bukkit.getScheduler().runTask(this, () -> {
	                player.kickPlayer(KickMessage);

	                if (Command != null)
	                    getServer().dispatchCommand(Bukkit.getConsoleSender(),
	                            Command.replace("%name%", player.getName()));
	            });

	            getLogger().warning(player.getName() + " tried to exploit CustomPayload: " + ex.getMessage());
	            event.setCancelled(true);
	        }
	    }

	    @SuppressWarnings("deprecation")
	    private void checkNbtTags(PacketEvent event) throws IOException {
	        PacketContainer container = event.getPacket();
	        ByteBuf buffer = container.getSpecificModifier(ByteBuf.class).read(0).copy();

	        byte[] bytes = new byte[buffer.readableBytes()];
	        buffer.readBytes(bytes);

	        try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(bytes))) {
	            ItemStack itemStack = StreamSerializer.getDefault().deserializeItemStack(inputStream);
	            if (itemStack == null)
	                throw new IOException("Unable to deserialize ItemStack");

	            NbtCompound root = (NbtCompound) NbtFactory.fromItemTag(itemStack);
	            if (root == null) {
	                throw new IOException("No NBT tag?!");
	            } else if (!root.containsKey("pages")) {
	                throw new IOException("No 'pages' NBT compound was found");
	            } else {
	                NbtList<String> pages = root.getList("pages");
	                if (pages.size() > 50)
	                    throw new IOException("Too much pages");

   throw new IOException("A very long page");

	            }
	        } finally {
	            buffer.release();
	        }
	    }

	    private void checkChannels(PacketEvent event) throws Exception {
	        int channelsSize = event.getPlayer().getListeningPluginChannels().size();

	        PacketContainer container = event.getPacket();
	        ByteBuf buffer = container.getSpecificModifier(ByteBuf.class).read(0).copy();

	        try {
	            for (int i = 0; i < buffer.toString(Charsets.UTF_8).split("\0").length; i++)
	                if (++channelsSize > 125)
	                    throw new IOException("Too much channels");
	        } finally {
	            buffer.release();
	        }
	    }

	    private boolean elapsed(long from, long required) {
	        return from == -1L || System.currentTimeMillis() - from > required;
	    }
	}
