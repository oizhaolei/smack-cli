package com.ruptech.chinatalk.smack;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.OfflineMessageManager;

import asg.cliche.Command;
import asg.cliche.ShellFactory;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.packet.VCard;

public class Main {
    private String account;
    private String password;
    String server;// = "127.0.0.1";
    int port;// = 5222;

    public static void main(String[] args) throws IOException {
        if (args.length == 4) {
            String server = args[0];
            int port = Integer.parseInt(args[1]);
            String account = args[2];
            String password = args[3];
            ShellFactory.createConsoleShell("xmpp", "", new Main(server, port, account, password)).commandLoop();
        }
    }

    Connection connection = null;

    private ChatManager chatManager;

    MessageListener messageListener = new MessageListener() {
        public void processMessage(Chat chat, Message message) {
            _info("GOT message: " + message.toXML());
        }

    };

    private Roster roster;

    public Main(String server, int port, String account, String password) {
        super();
        this.server = server;
        this.port = port;
        this.account = account;
        this.password = password;
    }

    private void _connect(String server, int port, String account, String password) throws Exception {
        SmackConfiguration.setLocalSocks5ProxyEnabled(false);
        ConnectionConfiguration config = new ConnectionConfiguration(server,
                port);
        connection = new XMPPConnection(config);
        // Connect to the server
        connection.connect();
        connection.login(account, password);
    }

    private Chat _createChat(String user, ChatManager cm) {
        return cm.createChat(user, messageListener);
    }

    private ChatManager _getChatManager() {
        if (chatManager == null) {
            chatManager = connection.getChatManager();
            chatManager.addChatListener(new ChatManagerListener() {

                @Override
                public void chatCreated(Chat chat, boolean createdLocally) {
                    if (!createdLocally)
                        chat.addMessageListener(messageListener);
                }
            });
        }
        return chatManager;
    }

    private Roster _getRoster() {
        if (roster == null) {
            roster = connection.getRoster();
        }
        return roster;
    }

    @Command
    public String addRosterGroup(String group) {
        Roster mRoster = _getRoster();
        mRoster.createGroup(group);
        return "ok";
    }

    @Command
    public String addEntry(String user, String alias, String group) {
        try {
            Roster mRoster = _getRoster();

            mRoster.createEntry(user, alias, new String[]{group});
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
            return "err";
        }
    }

    @Command
    public String getNameForJID(String jid) {
        Roster roster = _getRoster();

        RosterEntry entry = roster.getEntry(jid);
        if (null != entry.getName()
                && entry.getName().length() > 0) {
            return entry.getName();
        } else {
            return jid;
        }
    }

    @Command
    public String userVCard(String user) {
        try {
            VCard vcard = new VCard();
            vcard.load(connection, user);

            InputStream in = new ByteArrayInputStream(
                    vcard.getAvatar());
            File avatar = new File("/tmp/user_" + user);
            OutputStream out = new FileOutputStream(avatar);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();
            out.close();
            System.out.print("SAVE TO: " + avatar.getPath());
            return vcard.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @Command
    public String groupList() {
        try {
            Roster roster = _getRoster();
            Collection<RosterGroup> groups = roster.getGroups();

            StringBuffer sb = new StringBuffer();
            for (RosterGroup group : groups) {

                sb.append(group.getName());
                sb.append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @Command
    public String entrieList() {
        try {
            if (!isAuthenticated()) {
                _connect(server, port, account, password);
            }

            Roster roster = _getRoster();
            Collection<RosterGroup> groups = roster.getGroups();

            StringBuffer sb = new StringBuffer();
            for (RosterGroup group : groups) {

                sb.append(group.getName()).append(":[\n");
                for (RosterEntry entry : group.getEntries()) {
                    sb.append('\t').
                            append(entry).append(',').
                            append(roster.getPresence(entry.getUser()).getFrom()).append(',').
                            append('\n');
                }
                sb.append(']');
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @Command
    public boolean isAuthenticated() {
        if (connection != null) {
            return (connection.isConnected() && connection
                    .isAuthenticated());
        }
        return false;
    }

    @Command
    public String login() {
        try {
            if (!isAuthenticated()) {
                _connect(server, port, account, password);
            }
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @Command
    public String offlineMessages() throws Exception {
        OfflineMessageManager offlineManager = new OfflineMessageManager(
                connection);
        try {
            Iterator<Message> it = offlineManager.getMessages();

            System.out.println(offlineManager.supportsFlexibleRetrieval());
            System.out.println("count: " + offlineManager.getMessageCount());

            StringBuffer sb = new StringBuffer("messages :[");

            while (it.hasNext()) {
                Message message = it.next();
                sb.append(message.toXML()).append(',').append('\n');
            }
            sb.append(']');

            offlineManager.deleteMessages();
            Presence presence = new Presence(Presence.Type.available);
            connection.sendPacket(presence);//上线了
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "err";
    }

    private void _info(String s) {
        System.out.print(s);
    }

    @Command
    public boolean logout() {
        try {
            if (connection != null) {
                chatManager = null;
                roster = null;
                connection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private RosterGroup _getRosterGroup(String groupName) {
        RosterGroup rosterGroup = _getRoster().getGroup(groupName);

        // create group if unknown
        if ((groupName.length() > 0) && rosterGroup == null) {
            rosterGroup = _getRoster().createGroup(groupName);
        }
        return rosterGroup;

    }

    private void _tryToRemoveUserFromGroup(RosterGroup group,
                                           RosterEntry rosterEntry) {
        try {
            group.removeEntry(rosterEntry);
        } catch (XMPPException e) {
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    private void _removeRosterEntryFromGroups(RosterEntry rosterEntry) {
        Collection<RosterGroup> oldGroups = rosterEntry.getGroups();

        for (RosterGroup group : oldGroups) {
            _tryToRemoveUserFromGroup(group, rosterEntry);
        }
    }

    @Command
    public String moveRosterItemToGroup(String userName, String groupName) {

        RosterGroup rosterGroup = _getRosterGroup(groupName);
        RosterEntry rosterEntry = _getRoster().getEntry(userName);

        _removeRosterEntryFromGroups(rosterEntry);

        try {
            rosterGroup.addEntry(rosterEntry);
            return "ok";
        } catch (XMPPException e) {
            e.printStackTrace();
        }
        return "err";
    }

    @Command
    public String removeRosterEntry(String user) {
        try {
            RosterEntry rosterEntry = _getRoster().getEntry(user);

            if (rosterEntry != null) {
                _getRoster().removeEntry(rosterEntry);
                return "ok";
            }
        } catch (XMPPException e) {
            e.printStackTrace();
        }
        return "err";
    }

    @Command
    public String removeRosterGroup(String group) {
        return "err";
    }

    @Command
    public String renameRosterGroup(String group, String newGroup) {
        RosterGroup groupToRename = _getRoster().getGroup(group);
        if (groupToRename != null) {
            groupToRename.setName(newGroup);
            return "ok";
        }
        return "err";
    }

    @Command
    public String renameRosterItem(String user, String newName) {
        RosterEntry rosterEntry = _getRoster().getEntry(user);

        if (newName.length() > 0 && rosterEntry != null) {
            rosterEntry.setName(newName);
            return "ok";
        }
        return "err";
    }

    @Command
    public String requestAuthorizationForRosterItem(String user) {
        Presence response = new Presence(Presence.Type.subscribe);
        response.setTo(user);
        connection.sendPacket(response);

        return "ok";
    }

    @Command
    public String headline(String user, String subject, String body) {
        try {
            Message newmsg = new Message();
            newmsg.setTo(user);
            newmsg.setSubject(subject);
            newmsg.setBody(body);
            newmsg.setType(Message.Type.headline);// normal支持离线
            connection.sendPacket(newmsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "ok";
    }

    @Command
    public String sendFile(String user, String file) {
        try {
            FileTransferManager manager = new FileTransferManager(connection);
            Presence pre = _getRoster().getPresence(user);
            OutgoingFileTransfer transfer = manager
                    .createOutgoingFileTransfer(pre.getFrom());
            transfer.sendFile(new File(file), "file...");
            while (!transfer.isDone()) {
            }
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "err";
    }

    @Command
    public String sendMessage(String user, String message) {
        try {
            ChatManager cm = _getChatManager();
            Chat chat = _createChat(user, cm);
            chat.sendMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "ok";
    }

    @Command
    public String sendServerPing() {
        return "ok";

    }

    @Command
    public void quit() {
        System.exit(0);
    }

    @Command
    public String setStatusFromConfig() {
        return "ok";
    }

    @Command
    public String deleteAccount() {
        try {
            connection.getAccountManager().deleteAccount();
            return "ok";
        } catch (Exception e) {
            return "err";
        }
    }

    @Command
    public void setPresence(int code) {
        Presence presence;
        switch (code) {
            case 0:
                presence = new Presence(Presence.Type.available);
                connection.sendPacket(presence);
                _info("state" + "设置在线");
                break;
            case 1:
                presence = new Presence(Presence.Type.available);
                presence.setMode(Presence.Mode.chat);
                connection.sendPacket(presence);
                _info("state" + "设置Q我吧");
                System.out.println(presence.toXML());
                break;
            case 2:
                presence = new Presence(Presence.Type.available);
                presence.setMode(Presence.Mode.dnd);
                connection.sendPacket(presence);
                _info("state" + "设置忙碌");
                System.out.println(presence.toXML());
                break;
            case 3:
                presence = new Presence(Presence.Type.available);
                presence.setMode(Presence.Mode.away);
                connection.sendPacket(presence);
                _info("state" + "设置离开");
                System.out.println(presence.toXML());
                break;
            case 4:
                Roster roster = connection.getRoster();
                Collection<RosterEntry> entries = roster.getEntries();
                for (RosterEntry entry : entries) {
                    presence = new Presence(Presence.Type.unavailable);
                    presence.setPacketID(Packet.ID_NOT_AVAILABLE);
                    presence.setFrom(connection.getUser());
                    presence.setTo(entry.getUser());
                    connection.sendPacket(presence);
                    System.out.println(presence.toXML());
                }
                // // 向同一用户的其他客户端发送隐身状态
                // presence = new Presence(Presence.Type.unavailable);
                // presence.setPacketID(Packet.ID_NOT_AVAILABLE);
                // presence.setFrom(connection.getUser());
                // presence.setTo(StringUtils.parseBareAddress(connection.getUser()));
                // connection.sendPacket(presence);
                // logInfo("state" + "设置隐身");
                break;
            case 5:
                presence = new Presence(Presence.Type.unavailable);
                connection.sendPacket(presence);
                _info("state" + "设置离线");
                break;
            default:
                break;
        }
    }

    // @Command
    // public String signup(String account, String password) {
    // Registration reg = new Registration();
    // reg.setType(IQ.Type.SET);
    // reg.setTo(connection.getServiceName());
    // reg.setUsername(account);//
    // 注意这里createAccount注册时，参数是username，不是jid，是“@”前面的部分。
    // reg.setPassword(password);
    // reg.addAttribute("android", "geolo_createUser_android");//
    // 这边addAttribute不能为空，否则出错。所以做个标志是android手机创建的吧！！！！！
    // PacketFilter filter = new AndFilter(new PacketIDFilter(
    // reg.getPacketID()), new PacketTypeFilter(IQ.class));
    // PacketCollector collector = ClientConServer.connection
    // .createPacketCollector(filter);
    // ClientConServer.connection.sendPacket(reg);
    // IQ result = (IQ) collector.nextResult(SmackConfiguration
    // .getPacketReplyTimeout());
    // }
}