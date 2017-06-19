package org.chernovia.lichess;

import java.net.URI;
import java.util.Collection;
import java.util.WeakHashMap;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class GameClient {
	private static final Logger LOG = Log.getLogger(GameClient.class);
    private WebSocketClient client;
    private WeakHashMap<String,GameThread> games;
    
    public class GameThread extends Thread {
    	private String gid;
        private GameSock sock;
    	public GameThread(GameSock s, String g) { sock = s; gid = new String(g); setName("Game Thread: " + gid); }
    	public void run() {
    		try { mainLoop(sock); }
        	catch (Exception e) { LOG.warn(e); try { Thread.sleep(1000); } catch (InterruptedException ignore) {} }
        	sock.end();
        	LOG.info("Exiting thread...");
        	games.remove(gid);
    	}
    	public GameSock getSock() { return sock; }
    }
    
    public GameClient() {
    	SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true); //magic!
        client = new WebSocketClient(sslContextFactory);
        games = new WeakHashMap<String,GameThread>();
    }
    
    public GameSock newGame(String gid, GameWatcher w) {
    	URI uri = URI.create("wss://socket.lichess.org/" + gid + "/white/socket?sri=zug" + (int)(Math.random() * 999)); 
        GameSock sock = new GameSock(); sock.addWatcher(w);
		ClientUpgradeRequest request = new ClientUpgradeRequest();
		try { client.start(); client.connect(sock,uri,request); }
		catch (Exception augh) { augh.printStackTrace(); return null; }
		GameThread newgame = new GameThread(sock,gid);
		games.put(gid,newgame); 
		newgame.start();
		return newgame.getSock();
    }
    
    public GameThread getGame(String gid) { return games.get(gid); }
    public Collection<GameThread> getGames() { return games.values(); }
    
    public void mainLoop(GameSock sock) throws InterruptedException {
   		while (sock.isConnecting() || sock.isConnected()) {
			Thread.sleep(2000); 
			sock.send("{\"t\":\"p\",\"v\":9999999}"); //HUGE number for socket version
   		}
    }
}
