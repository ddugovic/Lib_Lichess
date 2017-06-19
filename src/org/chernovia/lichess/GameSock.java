package org.chernovia.lichess;

import java.util.ArrayList;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket
public class GameSock {
	
	private static final Logger LOG = Log.getLogger(GameSock.class);
	private ArrayList<GameWatcher> watchers;
    public void addWatcher(GameWatcher gw) { watchers.add(gw); }
    public void removeWatcher(GameWatcher gw) { watchers.remove(gw); }
    private Session session;
    
    public GameSock() {
    	watchers = new ArrayList<GameWatcher>();
    }
    
    public boolean isConnecting() { return (session == null); }
    public boolean isConnected() { return (session != null && session.isOpen()); }
    
    public void end() {	if (session!=null) session.close(); }
    
    public void send(String msg) {
    	if (isConnected()) session.getRemote().sendStringByFuture(msg);
    }

    @OnWebSocketConnect
    public void onConnect(Session sess) {
        LOG.info("onConnect({})",sess);
        session = sess;
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        LOG.info("onClose({}, {})", statusCode, reason);
    	if (session != null) session.close();
    	for (GameWatcher gw : watchers) gw.finished(this);
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        LOG.warn(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        for (GameWatcher gw : watchers) gw.gameMsg(this,msg);
    }
}
