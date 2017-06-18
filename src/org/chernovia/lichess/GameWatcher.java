package org.chernovia.lichess;

public interface GameWatcher {
	public void gameMsg(String message);
	public void finished();
}
