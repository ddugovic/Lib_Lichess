package org.chernovia.lichess;

import java.net.URI;
import java.util.Vector;
import java.util.concurrent.Future;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;

@WebSocket
public class GameSock extends Thread {
	private static final Logger LOG = Log.getLogger(GameSock.class);
	private Vector<GameWatcher> watchers;
    private String uri;
    private WebSocketClient client;
    private Session session;
    
    public GameSock(String l) {
    	uri = l;
    	watchers = new Vector<GameWatcher>();
    	SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true); //magic!
        client = new WebSocketClient(sslContextFactory);
    }
    
    public void addWatcher(GameWatcher gw) { watchers.add(gw); }
    public void removeWatcher(GameWatcher gw) { watchers.remove(gw); }
    
    public void run() {
    	try {
    		client.start();
    		Future<Session> fut = client.connect(this,URI.create(uri));
    		session = fut.get();
    		//Thread.sleep(2000); session.getRemote().sendString("{\"t\":\"startWatching\",\"d\":\"" + id + "\"}");
    		mainLoop();
    	}
    	catch (Exception e) { LOG.warn(e); try { Thread.sleep(1000); } catch (InterruptedException ignore) {} }
    	if (session != null && session.isOpen()) session.close(); 
    	for (GameWatcher gw : watchers) gw.finished();
    }
    
    public void mainLoop() throws InterruptedException {
   		while (session.isOpen()) {
			Thread.sleep(2000); 
			session.getRemote().sendStringByFuture("{\"t\":\"p\",\"v\":9999999}"); //HUGE number for socket version
   		}
    }
    
    public void end() {	if (session !=null) session.close(); interrupt(); }
    
    public void send(String msg) {
    	LOG.info("SENDING: " + msg);
    	//session.getRemote().sendStringByFuture("{\"t\":\"p\",\"v\":9999999}"); 
    	session.getRemote().sendStringByFuture(msg);
    }

    @OnWebSocketConnect
    public void onConnect(Session sess) {
        LOG.info("onConnect({})",sess);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        LOG.info("onClose({}, {})", statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        LOG.warn(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        //LOG.info("onMessage() - {}", msg);
        for (GameWatcher gw : watchers) gw.gameMsg(msg);
    }
}
