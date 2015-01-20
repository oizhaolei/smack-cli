package com.ruptech.chinatalk.smack;

public interface Smack {
	public boolean login(String account, String password);

	public boolean logout();

	public boolean isAuthenticated();

	public void addRosterItem(String user, String alias, String group);

	public void removeRosterItem(String user);

	public void renameRosterItem(String user, String newName);

	public void moveRosterItemToGroup(String user, String group);

	public void renameRosterGroup(String group, String newGroup);

	public void requestAuthorizationForRosterItem(String user);

	public void addRosterGroup(String group);

	public void setStatusFromConfig();

	public void sendMessage(String user, String message);

	public void sendServerPing();

	public String getNameForJID(String jid);
}
