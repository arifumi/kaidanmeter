/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2011 Adam Nyb蜉ヌ
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device just discovered.
 */
class kaidanMove implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public long timestamp_start;
	public long timestamp_end;
	public Integer floor_num_start;
	public Integer floor_num_end;
	public String floor_str_start;
	public String floor_str_end;
	public Integer flag_done;
}

public class KaidanMeter extends Activity {
	private final int FP = ViewGroup.LayoutParams.FILL_PARENT; 
	private final int WC = ViewGroup.LayoutParams.WRAP_CONTENT; 
	
    private LinearLayout mTagContent;
    private TextView statusTV;
    private String sessionid = "";
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    public kaidanMove pendingMove;

    Handler mHandler = new Handler();
    private String mId = null;
    private Timer uptimer = null;
    private CookieManager manager;
    public ArrayList<kaidanMove> moveList;
    public HashMap<String, ArrayList<String>> kaidanMap;

    public String nickName = null;
    
    private static final int MENU_ID_MENU1 = (Menu.FIRST + 1);
    private static final int MENU_ID_MENU2 = (Menu.FIRST + 2);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_viewer);
        System.out.println("onCreate sessionid="+sessionid+",nickname="+nickName);

        LinearLayout ll = (LinearLayout) findViewById(R.id.layout_main);

    	moveList = new ArrayList<kaidanMove>();
    	kaidanMap = new HashMap<String, ArrayList<String>>();
    	pendingMove = null;
    	
    	ImageView image1 = new ImageView(this);
    	image1.setImageResource(R.drawable.kdn48banner);
    	image1.setAlpha(150);
    	ll.addView(image1, new LinearLayout.LayoutParams(WC,WC));

        statusTV = new TextView(this);
        statusTV.setText("");
        statusTV.setTextSize(20);
        statusTV.setTextColor(Color.BLACK);
        statusTV.setHeight(100);
        statusTV.setBackgroundColor(Color.argb(100, 0, 200, 255));
        statusTV.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
        ll.addView(statusTV, new LinearLayout.LayoutParams(FP, WC));

        mTagContent = new LinearLayout(this);
        mTagContent.setOrientation(LinearLayout.VERTICAL);
        ll.addView(mTagContent, new LinearLayout.LayoutParams(FP, FP));
        
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            mAdapter = NfcAdapter.getDefaultAdapter(this);
            mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass())
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        }
      
        // sessionidの読み出し
        SharedPreferences sp = getSharedPreferences("sessionid", Context.MODE_PRIVATE);
        sessionid = sp.getString("id","");
        nickName = sp.getString("nick","");
        
        // idListをシリアライズ化から戻す
        
        // ニックネームの設定を促す
        if ( nickName == null || nickName == "" ) {
        	onOptionsItemSelected(null);
        }
        if (statusTV.getText()=="") {
        	statusTV.setText("階段の利用を開始する階で、ICタグを読み取ってください");
        }
    }

    public boolean onCreateOptionsMenu(Menu menu){
      	super.onCreateOptionsMenu(menu);
    	menu.add(0 , MENU_ID_MENU1 , Menu.NONE , "Settings").setIcon(android.R.drawable.ic_menu_edit);
    	menu.add(0 , MENU_ID_MENU2 ,Menu.NONE , "About").setIcon(android.R.drawable.ic_menu_info_details);
   	 	return true;
   }
   
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
	   int s;
       final EditText edtInput;
   		if (item == null) {
   			s = MENU_ID_MENU1;
   		} else {
   			s = item.getItemId();
   		}
   	
   		switch (s) {
   		case MENU_ID_MENU1:
   			// Create EditText
   			edtInput = new EditText(this);
   			edtInput.setInputType(InputType.TYPE_CLASS_TEXT);
   			edtInput.setText(nickName);
   			// Show Dialog
   			new AlertDialog.Builder(this)
   			.setIcon(R.drawable.kdn48icon2)
   			.setTitle("ニックネーム設定")
   			.setView(edtInput)
   			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
   				public void onClick(DialogInterface dialog, int whichButton) {
   					nickName = edtInput.getText().toString();
   				}
   			})
   			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
   				public void onClick(DialogInterface dialog, int whichButton) {
   				}
   			})
   			.show();
   			break;
		case MENU_ID_MENU2:
     	   	new AlertDialog.Builder(this)
     	   	.setIcon(R.drawable.kdn48icon2)
     	   	.setTitle("スマホで階段利用プロジェクト")
     	   	.setMessage("Developed by Arifumi Matsumoto.\nPromoted and supported by 武蔵野地区通研分会\n"+sessionid)
     	   	.setPositiveButton("OK", new DialogInterface.OnClickListener() {
     	   		public void onClick(DialogInterface dialog, int whichButton) {
     	   		}
     	   	})
     	   	.show();
     	   	break;
   		}
   		return true;
   }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
        //    mAdapter.enableForegroundNdefPush(this, mNdefPushMessage);
        }
        manager = new CookieManager();  
//   	 CookieHandler のデフォルトに設定
        CookieHandler.setDefault(manager);
        // sessionidの読み出し
        SharedPreferences sp = getSharedPreferences("sessionid", Context.MODE_PRIVATE);
        sessionid = sp.getString("id","");
        nickName = sp.getString("nick","");
        System.out.println("onResume sessionid="+sessionid+",nickname="+nickName);

        try {
            FileInputStream fis = openFileInput("pendingmove.dat");
            ObjectInputStream ois = new ObjectInputStream(fis);
            pendingMove = (kaidanMove) ois.readObject();
            ois.close();
        } catch (ClassNotFoundException e) {
        	e.printStackTrace();
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
    		e.printStackTrace();
        }

        // idListをシリアライズ化から戻す
        
        // ニックネームの設定を促す
        if ( nickName == null ) {
        	onOptionsItemSelected(null);
        }
        
        // スレッドを動かす
        uptimer = new Timer();
        uptimer.schedule(
        		new TimerTask() {
        			
        			@Override
        			public void run() {
        				// アップロード処理
        				int i = 0;
        				ArrayList<kaidanMove> delList = new ArrayList<kaidanMove>();
        				//System.out.println("movelist size ="+moveList.size());
        				for(Iterator<kaidanMove> it = moveList.iterator(); it.hasNext() ; ){
        					final kaidanMove move = it.next();

        					int result = httpPostRequest("start="+move.floor_num_start+"&end="+move.floor_num_end+"&nick="+nickName, "UTF-8");
        				    System.out.println(i+":"+move.floor_num_start+"->"+move.floor_num_end+"="+result);
         				    i++;
        				    if (result>0) {
        				    	delList.add(move);
        				    }
        				}
        				final ArrayList<kaidanMove> fdelList = new ArrayList<kaidanMove>(delList);
				    	mHandler.post(new Runnable() {
    						public void run() {
				    	        // アップロードが成功した場合は、idListから該当IDを削除する
    	        				for(Iterator<kaidanMove> it = fdelList.iterator(); it.hasNext() ; ){
    								moveList.remove(it.next());  
    								buildTagViews("データをアップロードしました");
    	        				}
    	        				//System.out.println("movelist size ="+moveList.size());
    	        				//buildTagViews("movelist size="+moveList.size());
				    		}
				    	});

        			}
        		}, 0, 10000);
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
        editor.putString("nick", nickName);
        if (pendingMove!=null) {
        	try {
        	    FileOutputStream fos = openFileOutput("pendingmove.dat", MODE_PRIVATE);
        	    ObjectOutputStream oos = new ObjectOutputStream(fos);
        	    oos.writeObject(pendingMove);
        	    oos.close();
        	} catch (FileNotFoundException e) {
        		e.printStackTrace();
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
        }
        editor.commit();
        System.out.println("onPause sessionid="+sessionid);
    }

    private void resolveIntent(Intent intent) {
    	System.out.println("resolveIntent:"+intent.toString());
        // Parse the intent
        String action = intent.getAction();
        if (action.equalsIgnoreCase(Intent.ACTION_SEND) && intent.hasExtra(Intent.EXTRA_TEXT)) {
            mId = intent.getStringExtra(Intent.EXTRA_TEXT); 

            System.out.println(mId+"をACTION_SENDから読み込みました");
        } else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
        		NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) ) {
        	try {
        		Parcelable tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        		mId = getmId(tag);
        	} catch (Exception e) {
        		e.printStackTrace();
        		throw new RuntimeException(e);
        	}
        	// Setup the views
        	//buildTagViews(msgs);
        	System.out.println(mId+"をTAG_DISCOVEREDから読み込みました");
        }
        
    	if (mId == null || mId == "" ) {
    		return;
    	}
    	
        if (kaidanMap.get(mId) == null) {
        	buildTagViews("不正なICタグです");
           	return;
        }

        if (pendingMove == null) {
         	System.out.println("pendingMove==null");
           	statusTV.setText("階段利用が終了した階でICタグを読み取ってください");
           	pendingMove = new kaidanMove();
           	pendingMove.floor_num_start = Integer.parseInt(kaidanMap.get(mId).get(0));
           	pendingMove.floor_str_start = kaidanMap.get(mId).get(1) + pendingMove.floor_num_start + "階";
           	pendingMove.timestamp_start = System.currentTimeMillis();
           	buildTagViews(pendingMove.floor_num_start+"階から利用開始");
        } else {
           	System.out.println("pendingMove!=null");
            	// 条件処理 (同じ階の場合は処理しない)
                statusTV.setText("別の階でICタグを読み取ってください");
            	pendingMove.floor_num_end = Integer.parseInt(kaidanMap.get(mId).get(0));
            	if (pendingMove.floor_num_end == pendingMove.floor_num_start) {
            			return;
            	}
            	pendingMove.floor_str_end = kaidanMap.get(mId).get(1) + pendingMove.floor_num_end + "階";
            	pendingMove.timestamp_end = System.currentTimeMillis();
            	// 条件処理 (時間処理) 1フロアにつき、3秒〜30秒の制限時間を付ける
            	int floors = Math.abs(pendingMove.floor_num_end - pendingMove.floor_num_start);
//        		floors = 1; //DEBUG
            	long duration = pendingMove.timestamp_end - pendingMove.timestamp_start;
            	if (duration > 3000 * floors && duration < 30000 * floors ) {
            		moveList.add(pendingMove);
        			buildTagViews(pendingMove.floor_num_end+"階で利用終了");
                    statusTV.setText("階段の利用お疲れさまでした");
                    pendingMove = null;            		
            	}
            }

        
    }
    
    
    
    private int httpPostRequest( String requestParams, String encode ){  
    	final String url_str = "http://vps.arifumi.net/mtsuken/post.php";

		System.out.println("in httpPostRequest("+requestParams+")");
    	if (sessionid == "") {
    		System.out.println("session id is not obtained.");
    		// cookieを取得しにいく	
    		try {
    			URL url = new URL(url_str);
				HttpURLConnection http = (HttpURLConnection)url.openConnection();
				http.setRequestMethod("POST");
	            http.setDoOutput(true);
	            PrintStream ps = new PrintStream(http.getOutputStream());
	            ps.print("");
	            ps.close();
				int response = http.getResponseCode();
				System.out.println("Response0: " + response);
				http.disconnect(); 
    		} catch (IOException e) {
    			e.printStackTrace();
    			System.out.println("IOException:"+e.getMessage());
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
            http.setDoOutput(true);
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
            e.printStackTrace();
 		   return -1;
        }

        return 1;
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

    void buildTagViews(String msg) {
        SimpleDateFormat timeFormat = new SimpleDateFormat();
        
        if (msg == null) {
            return;
        }

        // Parse the first message in the list
        // Build views for all of the sub records
        Date now = new Date();
        TextView timeView = new TextView(this);
        timeView.setTextColor(Color.BLACK);
        timeView.setText(timeFormat.format(now));
        timeView.setTextSize(18);
        timeView.append(" "+msg);
        mTagContent.addView(timeView, 0);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }
}
