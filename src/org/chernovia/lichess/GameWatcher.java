package org.chernovia.lichess;

public interface GameWatcher {
	public void gameMsg(GameSock sock, String message);
	public void finished(GameSock sock);
}
