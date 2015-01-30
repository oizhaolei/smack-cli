package com.ruptech.chinatalk.smack;

import asg.cliche.Command;
import asg.cliche.ShellFactory;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.OfflineMessageManager;
import org.jivesoftware.smackx.ReportedData;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.packet.VCard;
import org.jivesoftware.smackx.search.UserSearchManager;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.*;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;

public class Main {
    String server;// = "127.0.0.1";
    int port;// = 5222;
    Connection connection = null;
    MessageListener messageListener = new MessageListener() {
        @Override
        public void processMessage(Chat chat, Message message) {
            //_playSound();
            _println("GOT message: " + message.toXML());

        }
    };
    private AccountManager accountManager;
    private String smackResource = "smackCli";


    public void _playSound() {
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(getClass().getResourceAsStream("office.mp3"));
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch (Exception ex) {
            System.out.println("Error with playing sound.");
            ex.printStackTrace();
        }
    }

    private String account;
    private String password;
    private ChatManager chatManager;
    private Roster roster;

    public Main(String server, int port, String account, String password) {
        super();
        this.server = server;
        this.port = port;
        this.account = account;
        this.password = password;

        login();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            //TODO commons-cli
            _println("java Main host port account password");
        } else {
            String server = args[0];
            int port = Integer.parseInt(args[1]);
            String account = args[2];
            String password = args[3];
            ShellFactory.createConsoleShell("xmpp", "", new Main(server, port, account, password)).commandLoop();
        }
    }

    private static byte[] _getFileBytes(File file) throws IOException {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            int bytes = (int) file.length();
            byte[] buffer = new byte[bytes];
            int readBytes = bis.read(buffer);
            if (readBytes != buffer.length) {
                throw new IOException("Entire file not read");
            }
            return buffer;
        } finally {
            if (bis != null) {
                bis.close();
            }
        }
    }

    private void _connect(String server, int port, String account, String password) throws Exception {
        SmackConfiguration.setLocalSocks5ProxyEnabled(false);
        ConnectionConfiguration config = new ConnectionConfiguration(server,
                port);
        connection = new XMPPConnection(config);
        // Connect to the server
        connection.connect();
        connection.login(account, password, smackResource);

        // TODO: merge to one?
        ProviderManager.getInstance().addExtensionProvider(FromLang.ELEMENT_NAME, FromLang.NAMESPACE, new FromLang.Provider());
        ProviderManager.getInstance().addExtensionProvider(ToLang.ELEMENT_NAME, ToLang.NAMESPACE, new ToLang.Provider());
        ProviderManager.getInstance().addExtensionProvider(Cost.ELEMENT_NAME, Cost.NAMESPACE, new Cost.Provider());
        ProviderManager.getInstance().addExtensionProvider(OriginId.ELEMENT_NAME, OriginId.NAMESPACE, new OriginId.Provider());
        String me = connection.getUser();
        _println(me);
        _getChatManager();
        //registerPacketListener();
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
            _println("SAVE TO: " + avatar.getPath());
            return vcard.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @Command
    public String changeImage(String f) {
        try {
            VCard vcard = new VCard();
            vcard.load(connection);

            byte[] bytes;


            bytes = _getFileBytes(new File(f));
            String encodedImage = new String(Base64.getEncoder().encode(bytes));
            vcard.setAvatar(bytes, encodedImage);
            vcard.setField("PHOTO", "<TYPE>image/jpg</TYPE><BINVAL>"
                    + encodedImage + "</BINVAL>", true);

            vcard.save(connection);
            return "ok";
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
    public String entryList() {
        try {
            if (!isAuthenticated()) {
                _connect(server, port, account, password);
            }
            StringBuffer sb = new StringBuffer();

            Roster roster = _getRoster();
            Collection<RosterEntry> entries = roster.getEntries();
            sb.append(":[\n");
            for (RosterEntry entry : entries) {
                sb.append('\t').
                        append(entry).append(',').
                        append(roster.getPresence(entry.getUser()).getFrom()).append(',').
                        append('\n');
            }
            sb.append(']');
            Collection<RosterGroup> groups = roster.getGroups();

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
    public String searchUsers(String str) {
        try {
            //TODO
            UserSearchManager usm = new UserSearchManager(connection);
            Form searchForm = null;
            searchForm = usm.getSearchForm(server);
            Form answerForm = searchForm.createAnswerForm();
            answerForm.setAnswer("Username", true);
            answerForm.setAnswer("search", str);
            ReportedData data = usm.getSearchResults(answerForm, server);

            StringBuffer sb = new StringBuffer();
            sb.append('[').append('\n');
            Iterator<ReportedData.Row> it = data.getRows();
            while (it.hasNext()) {
                ReportedData.Row row = it.next();
                sb.append(row.getValues("Username").next()).append(',').append('\n');
                sb.append(row.getValues("Name").next()).append(',').append('\n');
                sb.append(row.getValues("Email").next()).append(',').append('\n');
            }
            return sb.append(']').toString();
        } catch (XMPPException e) {
            e.printStackTrace();
            return "err";
        }
    }

    @Command
    public String login(String server, int port, String account, String password) {
        this.server = server;
        this.port = port;
        this.account = account;
        this.password = password;
        return login();
    }

    @Command
    public String getAccountAttributes() {
        Collection<String> attributes = _getAccountManager().getAccountAttributes();
        StringBuffer sb = new StringBuffer();
        for (String name : attributes) {
            sb.append(name).append(':');
            String attr = _getAccountManager().getAccountAttribute(name);
            sb.append(attr).append('\n');
        }
        return sb.toString();
    }

    @Command
    public String getUser() {
        return connection.getUser();
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

            _println("count: " + offlineManager.getMessageCount());

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

    private static void _println(String s, Object... args) {
        System.out.println(String.format(s, args));
    }

    @Command
    public boolean logout() {
        try {
            if (connection != null) {
                chatManager = null;
                accountManager = null;
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
    public String sendMessage(String user, String text) {
        try {
            ChatManager cm = _getChatManager();
            Chat chat = _createChat(user, cm);

            Message message = new Message(chat.getParticipant(), Message.Type.chat);
            message.setThread(chat.getThreadID());
            message.setBody(text);

            message.addExtension(new FromLang("CN"));
            message.addExtension(new ToLang("EN"));

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
            _getAccountManager().deleteAccount();
            return "ok";
        } catch (Exception e) {
            return "err";
        }
    }

    private AccountManager _getAccountManager() {
        if (accountManager == null) {
            accountManager = connection.getAccountManager();
        }
        return accountManager;
    }

    @Command
    public void setPresence(String code) {
        Presence presence;
        switch (code) {
            case "available":
                presence = new Presence(Presence.Type.available);
                break;
            case "chat":
                presence = new Presence(Presence.Type.available);
                presence.setMode(Presence.Mode.chat);
                break;
            case "dnd":
                presence = new Presence(Presence.Type.available);
                presence.setMode(Presence.Mode.dnd);
                break;
            case "away":
                presence = new Presence(Presence.Type.available);
                presence.setMode(Presence.Mode.away);
                break;
            case "unavailable":
                presence = new Presence(Presence.Type.unavailable);
                break;
            default:
                presence = new Presence(Presence.Type.available);
                break;
        }
        connection.sendPacket(presence);
    }

    @Command
    public String createAccount(String account, String password) {
        try {
            _getAccountManager().createAccount(account, password);
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
            return "err";
        }
    }
}