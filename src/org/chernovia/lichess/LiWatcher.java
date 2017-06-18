package org.chernovia.lichess;

public interface LiWatcher {
	public void lisock_msg(String message);
	public void lisock_fin();
}
