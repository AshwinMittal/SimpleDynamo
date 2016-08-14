package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {
	static final String TAG = SimpleDynamoProvider.class.getSimpleName();

	static final int SERVER_PORT = 10000;
	static final String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
	public HashMap<String, String> messages = new HashMap<String, String>();
	public int queryAllFlag = 0;
	public String succNodeId;
	public String succ2NodeId;
	public String predNodeId;
	public String myNodeId;
	public String myPort;
	public TreeMap<String, String> hashVals = new TreeMap<String, String>();
	private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo.provider");

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		MsgObj msgObj = new MsgObj("DELETE",myNodeId,selection,"NULL");
		if(selection.equals("*")){
			String[] filenames = getContext().fileList();
			for (String filename : filenames) {
				getContext().deleteFile(filename);
			}
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(),
					String.valueOf((Integer.parseInt(succNodeId) * 2)));
			return 0;
		}
		else if(selection.equals("@")){
			String[] filenames = getContext().fileList();
			for (String filename : filenames) {
				getContext().deleteFile(filename);
			}
		}
		else{
			try {
				if(!checkHashVal(selection)){
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(),
							String.valueOf((Integer.parseInt(succNodeId) * 2)));
					return 0;
				}
				else {
					getContext().deleteFile(selection);
					msgObj.msgType = "DELETE-1";
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(),
							String.valueOf((Integer.parseInt(succNodeId) * 2)));
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		MsgObj msgObj = new MsgObj("INSERT",myNodeId,values.get("key").toString(),values.get("value").toString());
		try {
			if(!checkHashVal(values.get("key").toString())){
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(),
						String.valueOf((Integer.parseInt(myNodeId) * 2)));
				return uri;
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
		}

		String filename = values.get("key").toString();
		String msg = values.get("value").toString() + "\n";
		FileOutputStream outputStream;
		try {
			outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
			outputStream.write(msg.getBytes());
			outputStream.close();
			msgObj.msgType = "REPLICATE";
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(),
					String.valueOf((Integer.parseInt(succNodeId) * 2)));
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(),
					String.valueOf((Integer.parseInt(succ2NodeId) * 2)));
		} catch (Exception e) {
			Log.e(TAG, "File write failed");
		}
		Log.v("insert", values.toString());
		return uri;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		final String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
		myNodeId = portStr;
		this.myPort = myPort;

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {

			Log.e(TAG, "Can't create a ServerSocket");
			return false;
		}
		try {
			setSucc();
			Thread.sleep(10);
		} catch (Exception e) {
			e.printStackTrace();
		}
		//Log.d("Pred",predNodeId);
		MsgObj msgObj = new MsgObj("RECOVER", myNodeId, succNodeId+"::"+succ2NodeId, predNodeId);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "MSG" + msgObj.createMsg(), Integer.toString(Integer.parseInt(succNodeId)*2));
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		String[] columnNames= {"key", "value"};
		MatrixCursor cursor = new MatrixCursor(columnNames);

		if(selection.equals("*")){
			String[] filenames = getContext().fileList();
			for(String filename : filenames){
				String msg = "";
				try{
					InputStream is = getContext().openFileInput(filename);
					BufferedInputStream bis = new BufferedInputStream(is);
					while(bis.available()>0){
						msg += (char) bis.read();
					}
				}
				catch (Exception e){

				}
				messages.put(filename,msg);
			}
			MsgObj msgObj = new MsgObj("QRYALL",myNodeId,succNodeId,"NULL");
			sendMsg("MSG"+msgObj.createMsg(),String.valueOf((Integer.parseInt(succNodeId) * 2)));
			while(queryAllFlag == 0){
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			//Log.d("HashMapSize", Integer.toString(messages.size()));
			Iterator it = messages.entrySet().iterator();
			while (it.hasNext()) {
				HashMap.Entry pair = (HashMap.Entry)it.next();
				cursor.addRow(new String[]{pair.getKey().toString(), pair.getValue().toString()});
			}
		}
		else if(selection.equals("@")){
			String[] filenames = getContext().fileList();
			for(String filename : filenames){
				String msg = "";

				try{
					InputStream is = getContext().openFileInput(filename);
					BufferedInputStream bis = new BufferedInputStream(is);
					while(bis.available()>0){
						msg += (char) bis.read();
					}
				}
				catch (Exception e){

				}
				cursor.addRow(new String[]{filename, msg.trim()});
			}
		}
		else{
			try {
				if(checkHashVal(selection)){
					String msg = "";
					try{
						InputStream is = getContext().openFileInput(selection);
						BufferedInputStream bis = new BufferedInputStream(is);
						while(bis.available()>0){
							msg += (char) bis.read();
						}
					}
					catch (Exception e){

					}
					cursor.addRow(new String[]{selection, msg.trim()});
				}
				else{
					MsgObj msgObj = new MsgObj("QRY",myNodeId,selection,"NULL");
					sendMsg("MSG"+msgObj.createMsg(),String.valueOf((Integer.parseInt(succNodeId) * 2)));
					while(messages.get(selection) == null){
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					cursor.addRow(new String[]{selection, messages.get(selection)});
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		Log.v("query", selection);
		return cursor;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	public void setSucc() throws NoSuchAlgorithmException {
		hashVals.put(genHash("5554"),"5554");
		hashVals.put(genHash("5556"),"5556");
		hashVals.put(genHash("5558"),"5558");
		hashVals.put(genHash("5560"),"5560");
		hashVals.put(genHash("5562"),"5562");
		String myHash = genHash(myNodeId);

		int i = 1;
		int succ = 0;
		int succ2 = 0;
		int pred = 0;
		for(TreeMap.Entry<String,String> entry : hashVals.entrySet()) {
			if(myHash.compareTo(entry.getKey())==0){
				succ = (i%5)+1;
				succ2 = (succ%5)+1;
				pred = i-1;
				if(pred==0) pred = 5;
				break;
			}
			i++;
		}
		int j = 1;
		for(TreeMap.Entry<String,String> entry : hashVals.entrySet()) {
			if(j == succ){
				succNodeId = entry.getValue();
			}
			if(j == succ2){
				succ2NodeId = entry.getValue();
			}
			if(j == pred){
				predNodeId = entry.getValue();
			}
			j++;
		}
	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];

			try
			{
				while(true){
					Socket socket = serverSocket.accept();
					BufferedReader bis = new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
					String rcvMsg = bis.readLine().trim();
					rcvMsg = rcvMsg.substring(rcvMsg.indexOf("G")+1, rcvMsg.length());
					Log.d("MsgRcvd",rcvMsg);
					String[] parseMsg = rcvMsg.split("~~");
					MsgObj msgObj = new MsgObj(parseMsg[0],parseMsg[1],parseMsg[2],parseMsg[3]);

					if(msgObj.msgType.equals("INSERT")){
						if(checkHashVal(msgObj.msgKey)){
							ContentValues cv = new ContentValues();
							cv.put("key", msgObj.msgKey);
							cv.put("value", msgObj.msgValue);
							getContext().getContentResolver().insert(mUri, cv);
						}
					}
					else if(msgObj.msgType.equals("REPLICATE")){
						String filename = msgObj.msgKey;
						String msg = msgObj.msgValue;
						FileOutputStream outputStream;
						try {
							outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
							outputStream.write(msg.getBytes());
							outputStream.close();
						} catch (Exception e) {
							Log.e(TAG, "File write failed");
						}
						Log.v("replicate", "Key: "+filename+" | Value: "+msg);
					}
					else if(msgObj.msgType.equals("QRY")){
						if(checkHashVal(msgObj.msgKey)){
							String getMsg = "";
							try{
								InputStream inpStrm = getContext().openFileInput(msgObj.msgKey);
								BufferedInputStream buff = new BufferedInputStream(inpStrm);
								while(buff.available()>0){
									getMsg += (char) buff.read();
								}
							}
							catch (Exception e){

							}
							msgObj.msgType = "RCV";
							msgObj.msgValue = getMsg;
							sendMsg("MSG"+msgObj.createMsg(), String.valueOf((Integer.parseInt(msgObj.msgSender) * 2)));
						}
						else{
							sendMsg("MSG"+msgObj.createMsg(), String.valueOf((Integer.parseInt(succNodeId) * 2)));
						}
					}
					else if(msgObj.msgType.equals("RCV")){
						messages.put(msgObj.msgKey, msgObj.msgValue);
					}
					else if(msgObj.msgType.equals("QRYALL")){
						if(msgObj.msgSender.equals(myNodeId)){
							queryAllFlag = 1;
						}
						else{
							if(msgObj.msgValue.equals("NULL")){
								msgObj.msgKey=""; msgObj.msgValue="";
							}
							String[] filenames = getContext().fileList();
							if(filenames.length>0){
								for(String filename : filenames){
									String getMsg = "";
									try{
										InputStream inpStrm = getContext().openFileInput(filename);
										BufferedInputStream buff = new BufferedInputStream(inpStrm);
										while(buff.available()>0){
											getMsg += (char) buff.read();
										}
									}
									catch (Exception e){

									}
									msgObj.msgValue += getMsg+"##";
									msgObj.msgKey += filename+"##";
								}
								msgObj.msgType = "RCVALL";
								sendMsg("MSG" + msgObj.createMsg(), String.valueOf((Integer.parseInt(msgObj.msgSender) * 2)));
							}

							msgObj.msgType = "QRYALL";
							msgObj.msgKey = succNodeId;
							msgObj.msgValue = "NULL";
							sendMsg("MSG" + msgObj.createMsg(), String.valueOf((Integer.parseInt(succNodeId) * 2)));
						}
					}
					else if(msgObj.msgType.equals("RCVALL")){
						String[] keys = msgObj.msgKey.split("##");
						String[] Vals = msgObj.msgValue.split("##");
						for(int i=0; i<Vals.length; i++){
							messages.put(keys[i],Vals[i]);
						}
					}
					else if(msgObj.msgType.equals("DELETE")){
						if(msgObj.msgKey.equals("*")){
							String[] filenames = getContext().fileList();
							if(filenames.length>0){
								for (String filename : filenames) {
									getContext().deleteFile(filename);
								}
							}
							if(!succNodeId.equals(msgObj.msgSender)){
								sendMsg("MSG" + msgObj.createMsg(), String.valueOf((Integer.parseInt(succNodeId) * 2)));
							}
						}
						else{
							if(checkHashVal(msgObj.msgKey)){
								getContext().deleteFile(msgObj.msgKey);
								msgObj.msgType = "DELETE-1";
								sendMsg("MSG" + msgObj.createMsg(), String.valueOf((Integer.parseInt(succNodeId) * 2)));
							}
							else{
								sendMsg("MSG" + msgObj.createMsg(), String.valueOf((Integer.parseInt(succNodeId) * 2)));
							}
						}
					}
					else if(msgObj.msgType.equals("DELETE-1") || msgObj.msgType.equals("DELETE-2")){
						getContext().deleteFile(msgObj.msgKey);
						if(msgObj.msgType.equals("DELETE-1")){
							msgObj.msgType = "DELETE-2";
							sendMsg("MSG" + msgObj.createMsg(), String.valueOf((Integer.parseInt(succNodeId) * 2)));
						}
					}
					else if(msgObj.msgType.equals("RECOVER")){
						String[] filenames = getContext().fileList();
						if(filenames.length>0){
							MsgObj insertMsg = new MsgObj("INSERT-MULT", myNodeId, "", "");
							MsgObj replicateMsg = new MsgObj("REPLICATE-MULT", myNodeId, "", "");
							for(String filename : filenames){
								if(checkHashVal2(filename,msgObj.msgSender,msgObj.msgValue) ||
										(checkHashVal(filename) && succ2NodeId.equals(msgObj.msgSender)) ||
										(checkHashVal(filename) && succNodeId.equals(msgObj.msgSender))){
									String getMsg = "";
									try{
										InputStream inpStrm = getContext().openFileInput(filename);
										BufferedInputStream buff = new BufferedInputStream(inpStrm);
										while(buff.available()>0){
											getMsg += (char) buff.read();
										}
									}
									catch (Exception e){

									}
									if(checkHashVal2(filename,msgObj.msgSender,msgObj.msgValue)){
										insertMsg.msgValue += getMsg+"##";
										insertMsg.msgKey += filename+"##";
									}
									if((checkHashVal(filename) && succ2NodeId.equals(msgObj.msgSender)) ||
											(checkHashVal(filename) && succNodeId.equals(msgObj.msgSender))){
										replicateMsg.msgValue += getMsg+"##";
										replicateMsg.msgKey += filename+"##";
									}
								}
							}
							if(!insertMsg.msgKey.equals("")){
								sendMsg("MSG" + insertMsg.createMsg(), String.valueOf((Integer.parseInt(msgObj.msgSender) * 2)));
							}
							if(!replicateMsg.msgKey.equals("")){
								sendMsg("MSG" + replicateMsg.createMsg(), String.valueOf((Integer.parseInt(msgObj.msgSender) * 2)));
							}
						}
					}
					else if(msgObj.msgType.equals("INSERT-MULT")){
						Log.d("MsgObj",msgObj.createMsg());
						String[] keys = msgObj.msgKey.split("##");
						String[] Vals = msgObj.msgValue.split("##");
						for(int i=0; i<Vals.length; i++){
							ContentValues cv = new ContentValues();
							cv.put("key", keys[i]);
							cv.put("value", Vals[i]);
							getContext().getContentResolver().insert(mUri, cv);
						}
					}
					else if(msgObj.msgType.equals("REPLICATE-MULT")){
						String[] filename = msgObj.msgKey.split("##");
						String[] msg = msgObj.msgValue.split("##");
						for(int i=0; i<msg.length; i++){
							FileOutputStream outputStream;
							try {
								outputStream = getContext().openFileOutput(filename[i], Context.MODE_PRIVATE);
								outputStream.write(msg[i].getBytes());
								outputStream.close();
							} catch (Exception e) {
								Log.e(TAG, "File write failed");
							}
							Log.v("replicate", "Key: "+filename+" | Value: "+msg[i]);
						}
					}
					socket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	public boolean checkHashVal(String key) throws NoSuchAlgorithmException {
		String predHash = genHash(predNodeId);
		String myHash = genHash(myNodeId);

		if(predHash.compareTo(myHash)==0) return true;
		String keyHash = genHash(key);
		if(predHash.compareTo(myHash)>0 && (keyHash.compareTo(predHash)>0 || keyHash.compareTo(myHash)<=0)) return true;
		if(keyHash.compareTo(predHash)>0 && keyHash.compareTo(myHash)<=0) return true;
		return false;
	}

	//For recovery node (pred)
	public boolean checkHashVal2(String key, String node, String node_pred) throws NoSuchAlgorithmException {
		String predHash = genHash(node_pred);
		String myHash = genHash(node);

		if(predHash.compareTo(myHash)==0) return true;
		String keyHash = genHash(key);
		if(predHash.compareTo(myHash)>0 && (keyHash.compareTo(predHash)>0 || keyHash.compareTo(myHash)<=0)) return true;
		if(keyHash.compareTo(predHash)>0 && keyHash.compareTo(myHash)<=0) return true;
		return false;
	}

	public String getSucc(String nodeId) throws NoSuchAlgorithmException {
		String myHash = genHash(nodeId);

		int i = 1;
		int succ = 0;
		int succ2 = 0;
		int pred = 0;
		for (TreeMap.Entry<String, String> entry : hashVals.entrySet()) {
			if (myHash.compareTo(entry.getKey()) == 0) {
				succ = (i % 5) + 1;
				succ2 = (succ % 5) + 1;
				pred = i-1;
				if(pred==0) pred = 5;
				break;
			}
			i++;
		}
		int j = 1;
		String succNodeId1 = "";
		String succ2NodeId1 = "";
		String predNodeId1 = "";
		for (TreeMap.Entry<String, String> entry : hashVals.entrySet()) {
			if (j == succ) {
				succNodeId1 = entry.getValue();
			}
			if (j == succ2) {
				succ2NodeId1 = entry.getValue();
			}
			if(j == pred){
				predNodeId1 = entry.getValue();
			}
			j++;
		}
		return succNodeId1+"::"+succ2NodeId1+"::"+predNodeId1;
	}

	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			String msgToSend = msgs[0];
			String remotePort = msgs[1];
			if(remotePort.equals(myPort)){
				try {
					multicast(msgToSend);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
			else{
				sendMsg(msgToSend, remotePort);
			}
			return null;
		}
	}
	private void sendMsg(String msgToSend, String remotePort){
		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(remotePort));

			OutputStream outputStream = socket.getOutputStream();
			DataOutputStream dos = new DataOutputStream(outputStream);
			dos.writeUTF(msgToSend);
			socket.close();
		} catch (UnknownHostException e) {
			Log.e(TAG, "ClientTask UnknownHostException");
		} catch (IOException e) {
			Log.e(TAG, "ClientTask socket IOException");
		}
	}

	private void multicast(String msg) throws NoSuchAlgorithmException {
		msg = msg.substring(msg.indexOf("G")+1, msg.length());
		String[] parseMsg = msg.split("~~");
		MsgObj msgObj = new MsgObj(parseMsg[0],parseMsg[1],parseMsg[2],parseMsg[3]);
		if(msgObj.msgType.equals("RECOVER")) recover("MSG"+msgObj.createMsg());

		for(int i=0;i<5;i++){
			String remotePort = REMOTE_PORT[i];
			String succ_pred = getSucc(Integer.toString(Integer.parseInt(remotePort)/2));
			String[] succ_pred_arr = succ_pred.split("::");
			if(checkHashVal2(msgObj.msgKey,Integer.toString(Integer.parseInt(remotePort)/2),succ_pred_arr[2]) && !myPort.equals(REMOTE_PORT[i])){
				try {
					Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(remotePort));

					OutputStream outputStream1 = socket1.getOutputStream();
					DataOutputStream dos1 = new DataOutputStream(outputStream1);
					dos1.writeUTF("MSG"+msgObj.createMsg());

					socket1.close();

					for(int j=0;j<succ_pred_arr.length-1;j++){
						Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
								Integer.parseInt(succ_pred_arr[j])*2);

						OutputStream outputStream = socket.getOutputStream();
						DataOutputStream dos = new DataOutputStream(outputStream);
						msgObj.msgType = "REPLICATE";
						dos.writeUTF("MSG"+msgObj.createMsg());

						socket.close();
					}
				} catch (UnknownHostException e) {
					Log.e(TAG, "ClientTask UnknownHostException");
				} catch (IOException e) {
					Log.e(TAG, "ClientTask socket IOException"+e);
				} catch (Exception e){
					Log.e(TAG, "ClientTask socket Exception");
				}
			}
		}
	}
	private void recover(String msg) throws NoSuchAlgorithmException {
		msg = msg.substring(msg.indexOf("G")+1, msg.length());
		String[] parseMsg = msg.split("~~");
		MsgObj msgObj = new MsgObj(parseMsg[0],parseMsg[1],parseMsg[2],parseMsg[3]);
		for(int i=0;i<5;i++){
			if(!myPort.equals(REMOTE_PORT[i])){
				try {
					String remotePort = REMOTE_PORT[i];
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(remotePort));

					OutputStream outputStream = socket.getOutputStream();
					DataOutputStream dos = new DataOutputStream(outputStream);
					dos.writeUTF("MSG"+msgObj.createMsg());
					socket.close();
				} catch (UnknownHostException e) {
					Log.e(TAG, "ClientTask UnknownHostException");
				} catch (IOException e) {
					Log.e(TAG, "ClientTask socket IOException");
				}
			}
		}
	}
}
