/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2011 Adam Nyb劃k
 * Copyright (C) 2011 Some Japanese person/company?
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.arifumi.kaidanmeter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import net.arifumi.kaidanmeter.record.ParsedNdefRecord;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device just discovered.
 */
class kaidanMove {
	public long timestamp_start;
	public long timestamp_end;
	public Integer floor_num_start;
	public Integer floor_num_end;
	public String floor_str_start;
	public String floor_str_end;
	public Integer flag_done;
	
	protected kaidanMove() {
		timestamp_start = 0;
		timestamp_end = 0;
		floor_num_start = null;
		floor_num_end = null;
		floor_str_start = "";
		floor_str_end = "";
		flag_done = null;
	}
}

public class KaidanMeter extends Activity {

    private SimpleDateFormat timeFormat = new SimpleDateFormat();
    private LinearLayout mTagContent;
    private String sessionid = "";
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private NdefMessage mNdefPushMessage;
    public kaidanMove pendingMove;

    Handler mHandler = new Handler();
    private String mId = null;
    private Timer uptimer = null;
    public String hoge = "";
    private CookieManager manager;
    public ArrayList<kaidanMove> moveList;
    public HashMap<String, ArrayList<String>> kaidanMap;
    public EditText edtInput;
    public String nickName = null;

    public boolean onCreateOptionsMenu(Menu menu){
    	 super.onCreateOptionsMenu(menu);
    	 getMenuInflater().inflate(R.menu.menu,menu);
    	 return true;
    }
    
    // メニューのアイテムが選択された時に実行されるメソッド
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	// Create EditText
    	edtInput = new EditText(this);
    	// Show Dialog
    	new AlertDialog.Builder(this)
    	.setIcon(R.drawable.icon)
    	.setTitle("Input your nickname")
    	.setView(edtInput)
    	.setPositiveButton("OK", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			/* OKボタンをクリックした時の処理 */
//    			new AlertDialog.Builder(KaidanMeter.this)
//    			.setTitle("食べるもの: " + edtInput.getText().toString())
//    			.show();
    			nickName = edtInput.getText().toString();
    		}
    	})
    	.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			/* Cancel ボタンをクリックした時の処理 */
    		}
    	})
    	.show();
    	return true;
    }
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_viewer);
        
        mTagContent = (LinearLayout) findViewById(R.id.list);
    	moveList = new ArrayList<kaidanMove>();
    	kaidanMap = new HashMap<String, ArrayList<String>>();
    	pendingMove = null;

    	try {
        	InputStream is = getAssets().open("kaidandata.txt");
        	BufferedReader br = new BufferedReader(new InputStreamReader(is));
    	      String line = "";
    	      while ((line = br.readLine()) != null){
    	    	  StringTokenizer st = new StringTokenizer(line,",");
    	    	  ArrayList<String> hoge = new ArrayList<String>();
    	    	  String mid = st.nextToken();
    	    	  while (st.hasMoreTokens()) {
    	    		  hoge.add(st.nextToken());
    	    	  }
    	    	  kaidanMap.put(mid, hoge);
    	      }
    	      br.close();
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
    		e.printStackTrace();
    	}

        resolveIntent(getIntent());
        onOptionsItemSelected(null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            mAdapter = NfcAdapter.getDefaultAdapter(this);
            mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass())
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            mNdefPushMessage = new NdefMessage(new NdefRecord[] { newTextRecord("Message from NFC Reader :-)",
                    Locale.ENGLISH, true) });
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
            mAdapter.enableForegroundNdefPush(this, mNdefPushMessage);
        }
        manager = new CookieManager();
        //	 CookieHandler のデフォルトに設定
        CookieHandler.setDefault(manager);    	// データの読み出し
        // sessionidの読み出し
        SharedPreferences sp = getSharedPreferences("sessionid", Context.MODE_PRIVATE);
        sessionid = sp.getString("id","");
        System.out.println("onResume sessionid="+sessionid);
        // nicknameの読み出し
//        SharedPreferences sp2 = getSharedPreferences("nick", Context.MODE_PRIVATE);
//        nickName = sp2.getString("nick","");
//        System.out.println("onResume nickname="+sessionid);
        
        // idListをシリアライズ化から戻す
        
        // スレッドを動かす
        uptimer = new Timer();
        uptimer.schedule(
        		new TimerTask() {
        			
        			@Override
        			public void run() {
        				// アップロード処理
        				int i = 0;
        				ArrayList<kaidanMove> delList = new ArrayList<kaidanMove>();
        				System.out.println("movelist size ="+moveList.size());
        				for(Iterator<kaidanMove> it = moveList.iterator(); it.hasNext() ; ){
        					final kaidanMove move = it.next();

        					int result = httpPostRequest("start="+move.floor_num_start+"&end="+move.floor_num_end, "UTF-8");
        				    System.out.println(i+":"+move.floor_num_start+"->"+move.floor_num_end+"="+result);
        				    i++;
        				    if (result>0) {
        				    	delList.add(move);
        				    }
        				}
        				final ArrayList<kaidanMove> fdelList = new ArrayList<kaidanMove>(delList);
				    	mHandler.post(new Runnable() {
    						public void run() {
				    	        LinearLayout content = mTagContent;

				    	        // アップロードが成功した場合は、idListから該当IDを削除する
    	        				for(Iterator<kaidanMove> it = fdelList.iterator(); it.hasNext() ; ){
    								moveList.remove(it.next());   					
    				    			// 画面からも消去する。
    				    	        content.removeViewAt(0);
    				    	        content.removeViewAt(0);
    				    	        content.removeViewAt(0);
    	        				}
    	        				System.out.println("movelist size ="+moveList.size());
				    		}
				    	});

        			}
        		}, 0, 5000);

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
            mAdapter.disableForegroundNdefPush(this);
        }
        // スレッドを止める
        if (uptimer != null) {
        	uptimer.cancel();
        }
        // idListをシリアライズ化して保存する。
        SharedPreferences sp = getSharedPreferences("sessionid", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("id",sessionid);
        editor.commit();
        System.out.println("onPause sessionid="+sessionid);
    }

    private void resolveIntent(Intent intent) {
        // Parse the intent

        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            // When a tag is discovered we send it to the service to be save. We
            // include a PendingIntent for the service to call back onto. This
            // will cause this activity to be restarted with onNewIntent(). At
            // that time we read it from the database and view it.
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
                try {
                    Parcelable tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                	mId = getmId(tag);
                } catch (Exception e) {
          		   e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else {
                // Unknown tag type
                byte[] empty = new byte[0];
                byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                Parcelable tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                byte[] payload = null;
                try {
                    payload = dumpTagData(tag).getBytes();
                    mId = getmId(tag);
                } catch (Exception e) {
                	e.printStackTrace();
                    throw new RuntimeException(e);
                }
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
                NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
                msgs = new NdefMessage[] { msg };
            }
            // Setup the views
            buildTagViews(msgs);
            
            if (pendingMove == null) {
            	pendingMove = new kaidanMove();
            	pendingMove.floor_num_start = Integer.parseInt(kaidanMap.get(mId).get(0));
            	pendingMove.floor_str_start = kaidanMap.get(mId).get(1) + pendingMove.floor_num_start + "階";
            	pendingMove.timestamp_start = System.currentTimeMillis();
            } else {
            	// 条件処理 (同じ階の場合は処理しない)
            	pendingMove.floor_num_end = Integer.parseInt(kaidanMap.get(mId).get(0));
            	if (pendingMove.floor_num_end == pendingMove.floor_num_start) {
            			return;
            	}
            	pendingMove.floor_str_end = kaidanMap.get(mId).get(1) + pendingMove.floor_num_end + "階";
            	pendingMove.timestamp_end = System.currentTimeMillis();
            	// 条件処理 (時間処理) 1フロアにつき、3秒〜30秒の制限時間を付ける
            	int floors = Math.abs(pendingMove.floor_num_end - pendingMove.floor_num_start);
            	long duration = pendingMove.timestamp_end - pendingMove.timestamp_start;
            	if (duration > 3000 * floors && duration < 30000 * floors ) {
                    // ArrayListÉkaidanMoveðËÁÞ
                    moveList.add(pendingMove);
                    pendingMove = null;            		
            	}
            }

        }
    }
    
    private int httpPostRequest( String requestParams, String encode ){  
    	final String url_str = "http://vps.arifumi.net/mtsuken/post.php";

    	if (sessionid == "") {
    		// cookieを取得しにいく	
    		try {
    			URL url = new URL(url_str);
				HttpURLConnection http = (HttpURLConnection)url.openConnection();
				http.setRequestMethod("POST");
				//         http.setDoOutput(true); // ←☆POSTによるデータ送信を可能にします
				// = "hoge=test&fuga=test2";
				PrintStream ps = new PrintStream(http.getOutputStream());
				ps.close();  	
				int response = http.getResponseCode();
				System.out.println("Response0: " + response);
				http.disconnect(); 
    		} catch (IOException e) {
        		// 例外処理
     		   e.printStackTrace();
     		   return -1;
        	}
    		// Cookie の表示
    		CookieStore store = manager.getCookieStore();
    		List<HttpCookie> cookies = store.getCookies();

        	for (HttpCookie cookie: cookies) {
            	if (cookie.getName().equals("PHPSESSID")) {
            		sessionid = cookie.getValue();
            	}
            	System.out.println("Cookie0["+cookie.getName()+"]: "+cookie.getValue());
        	}
    	}

	// Cookieの復元
    	HttpCookie cookiesess = new HttpCookie("PHPSESSID", sessionid);
    	cookiesess.setDomain("vps.arifumi.net");
    	cookiesess.setPath("/mtsuken");
    	cookiesess.setVersion(0);
    	try {
    		manager.getCookieStore().add(new URI(url_str), cookiesess);
    	} catch (URISyntaxException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    		return -1;
    	}

        
        // サーバに接続
        try {
            URL url = new URL(url_str);
            HttpURLConnection http = (HttpURLConnection)url.openConnection();
            http.setRequestMethod("POST");
            http.setDoOutput(true); // ←☆POSTによるデータ送信を可能にします
            // = "hoge=test&fuga=test2";
            PrintStream ps = new PrintStream(http.getOutputStream());
            ps.print(requestParams);
            ps.close();
            
            int response = http.getResponseCode();
            System.out.println("Response: " + response);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(http.getInputStream()));

            while (true){
            	String line = reader.readLine();
                if ( line == null ){
                	break;
            	}
            	System.out.println(line);
            }
            reader.close();
            http.disconnect();        
        } catch (IOException e) {
            // 例外処理
 		   e.printStackTrace();
 		   return -1;
        }

        return 1;
    }  

    
    private NdefRecord newTextRecord(String text, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));

        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = text.getBytes(utfEncoding);

        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);

        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }
    
    /**
     * The reflection stuff in this method is copied from some Japanies site for backwards compatibility with Android
     * 2.3-2.3.2.
     * @throws IllegalAccessException 
     * @throws IllegalArgumentException 
     */
    private String getmId(Parcelable p) throws IllegalArgumentException, IllegalAccessException {
        Field f = null;
        Class<?> tagClass = p.getClass();

        try {
            f = tagClass.getDeclaredField("mId");
            f.setAccessible(true);
            byte[] mId = (byte[]) f.get(p);
            return getHex(mId);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }  
        return null;
    }
    private String dumpTagData(Parcelable p) throws SecurityException, IllegalArgumentException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();
        Field f = null;
        Class<?> tagClass = p.getClass();

        try {
            f = tagClass.getDeclaredField("mId");
            f.setAccessible(true);
            byte[] mId = (byte[]) f.get(p);
 //           sb.append("Tag ID (hex): ").append(getHex(mId)).append("\n");
 //           sb.append("Tag ID (dec): ").append(getDec(mId)).append("\n");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        System.out.println(sb.toString());

        try {
            f = tagClass.getDeclaredField("mRawTargets");
            f.setAccessible(true);
            String[] mRawTargets = (String[]) f.get(p);
            sb.append("Targets: ");
            if (mRawTargets.length == 1)
                sb.append(translateTarget(mRawTargets[0]));
            else {
                for (String s : mRawTargets) {
                    sb.append(translateTarget(s)).append(", ");
                }
            }
            sb.append("\n");
        } catch (NoSuchFieldException e) {
            String prefix = "android.nfc.tech.";
            Tag tag = (Tag) p;
            sb.append("Technologies: ");
            for (String tech : tag.getTechList()) {
                sb.append(tech.substring(prefix.length()));
                sb.append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append('\n');
            for (String tech : tag.getTechList()) {
                if (tech.equals(MifareClassic.class.getName())) {
                    MifareClassic mifareTag = MifareClassic.get(tag);
                    String type = "Unknown";
                    switch (mifareTag.getType()) {
                    case MifareClassic.TYPE_CLASSIC:
                        type = "Classic";
                        break;
                    case MifareClassic.TYPE_PLUS:
                        type = "Plus";
                        break;
                    case MifareClassic.TYPE_PRO:
                        type = "Pro";
                        break;
                    }
                    sb.append("Mifare Classic type: ");
                    sb.append(type);
                    sb.append('\n');

                    sb.append("Mifare size: ");
                    sb.append(mifareTag.getSize() + " bytes");
                    sb.append('\n');

                    sb.append("Mifare sectors: ");
                    sb.append(mifareTag.getSectorCount());
                    sb.append('\n');

                    sb.append("Mifare blocks: ");
                    sb.append(mifareTag.getBlockCount());
                    sb.append('\n');
                }

                if (tech.equals(MifareUltralight.class.getName())) {
                    MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                    String type = "Unknown";
                    switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                    }
                    sb.append("Mifare Ultralight type: ");
                    sb.append(type);
                    sb.append('\n');
                }
            }
        }
/*
        try {
            f = tagClass.getDeclaredField("mPollBytes");
            f.setAccessible(true);
            byte[] mPollBytes = (byte[]) f.get(p);
            if (mPollBytes != null && mPollBytes.length > 0)
                sb.append("Poll (hex): ").append(getHex(mPollBytes)).append("\n");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        try {
            f = tagClass.getDeclaredField("mActivationBytes");
            f.setAccessible(true);
            byte[] mActivationBytes = (byte[]) f.get(p);
            if (mActivationBytes != null && mActivationBytes.length > 0)
                sb.append("Activation (hex): ").append(getHex(mActivationBytes));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
*/
        return sb.toString();
    }

    private Object translateTarget(String target) {
        if ("iso14443_3a".equals(target))
            return "ISO 14443-3A";
        else if ("iso14443_3b".equals(target))
            return "ISO 14443-3B";
        else if ("iso14443_4".equals(target))
            return "ISO 14443-4";
        else if ("iso15693".equals(target))
            return "ISO 15693 (RFID)";
        else if ("jis_x_6319_4".equals(target))
            return "JIS X-6319-4 (FeliCa)";
        else if ("other".equals(target))
            return "Unknown";

        return target;
    }

    private String getHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i != bytes.length - 1) {
                sb.append("");
            }
        }
        return sb.toString();
    }

    private long getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = mTagContent;

        // Parse the first message in the list
        // Build views for all of the sub records
        Date now = new Date();
        List<ParsedNdefRecord> records = NdefMessageParser.parse(msgs[0]);
        final int size = records.size();
        for (int i = 0; i < size; i++) {
            TextView timeView = new TextView(this);
            timeView.setText(timeFormat.format(now));
            timeView.append("　階段を１階分、使用しました");
            content.addView(timeView, 0);
            ParsedNdefRecord record = records.get(i);
            content.addView(record.getView(this, inflater, content, i), 1 + i);
            content.addView(inflater.inflate(R.layout.tag_divider, content, false), 2 + i);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);

        
    }
}