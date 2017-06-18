package org.chernovia.lichess;

import org.chernovia.lichess.gson.LichessUser;
import org.chernovia.lichess.util.LichessUtils;

public class Test {

	public static void main(String[] args) {
		LichessUser user = LichessUtils.getUser("TwitchPlaysLichess");
		System.out.println(user.profile.country);
		System.out.println(user.playing);
		System.out.println(user.username);
	}

}
