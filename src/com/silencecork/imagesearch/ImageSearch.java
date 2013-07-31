package com.silencecork.imagesearch;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ImageSearch {
	
//	private static final String URL = "https://picasaweb.google.com/data/feed/api/all?kind=photo&tag=taipei,Landscape&max-results=10&v=2.0&alt=json&fields=entry(media:group(media:content))&imgmax=1024&q=taipei&prettyprint=true";
	private static final Uri FEED = Uri.parse("https://picasaweb.google.com/data/feed/api/all");
	private static ImageSearch sInstance;
	private static int MAX = 25;
	private static int MIN = 10;
	private boolean mIsInProgress;
	
	private ImageSearch() {
		
	}
	
	public static ImageSearch getInstance() {
		if (sInstance == null) {
			sInstance = new ImageSearch();
		}
		return sInstance;
	}
	
	public void search(final Context context, final String keyword, final String tag, final OnImageSearchCompleteListener listener) {
		if (mIsInProgress) {
			System.out.println("refresh already in progress");
			return;
		}
		mIsInProgress = true;
		AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {

			@Override
			protected Bitmap doInBackground(Void... params) {
				Uri.Builder builder = FEED.buildUpon();
				builder.appendQueryParameter("kind", "photo");
				if (!TextUtils.isEmpty(tag)) {
					builder.appendQueryParameter("tag", tag);
				}
				if (!TextUtils.isEmpty(keyword)) {
					builder.appendQueryParameter("q", keyword);
				}
				int num = MIN + (int)(Math.random() * ((MAX - MIN) + 1));
				num = (num <= 0) ? MIN : num;
				builder.appendQueryParameter("v", "2.0");
				builder.appendQueryParameter("alt", "json");
				builder.appendQueryParameter("fields", "entry(media:group(media:content))");
				builder.appendQueryParameter("max-results", String.valueOf(num));
				builder.appendQueryParameter("imgmax", "1024");
				Uri u = builder.build();
				System.out.println("url " + u.toString());
				HttpURLConnection conn = null;
				InputStream in = null;
				try {
					URL url = new URL(u.toString());
					conn = (HttpURLConnection) url.openConnection();
					in = conn.getInputStream();
					String response = streamToString(in);
					System.out.println("response: " + response);
					
					List<String> ret = parse(response);
					if (ret != null && ret.size() > 0) {
						int select = 0 + (int)(Math.random() * (((ret.size() - 1) - 0) + 1));
						String strUrl = ret.get(select);
						File f = new File(context.getFilesDir(), "tmp.jpg");
						if (downloadImage(strUrl, f)) {
							Bitmap b = decode(f.getAbsolutePath());
							return b;
						}
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				} finally {
					disconnect(conn);
					close(in);
				}
				return null;
			}

			@Override
			protected void onPostExecute(Bitmap result) {
				super.onPostExecute(result);
				if (listener != null) {
					if (result != null) { 
						listener.onComplete(result);
					} else {
						listener.onError();
					}
				}
				mIsInProgress = false;
			}
		};
		
		task.execute();
		
	}
	
	private void disconnect(HttpURLConnection conn) {
		if (conn != null) {
			conn.disconnect();
		}
	}
	
	private void close(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private List<String> parse(String response) throws JSONException {
		if (TextUtils.isEmpty(response)) {
			return null;
		}
		List<String> ret = new ArrayList<String>();
		JSONObject jsonObject = new JSONObject(response);
		JSONObject feedObject = jsonObject.getJSONObject("feed");
		JSONArray entries = feedObject.getJSONArray("entry");
		if (entries == null) {
			return ret;
		}
		int entriesLength = entries.length();
		for (int i = 0; i < entriesLength; i++) {
			JSONObject entry = entries.getJSONObject(i);
			if (!entry.has("media$group")) {
				continue;
			}
			JSONObject groupEntry = entry.getJSONObject("media$group");
			if (!groupEntry.has("media$content")) {
				continue;
			}
			JSONArray content = groupEntry.getJSONArray("media$content");
			if (content.length() <= 0) {
				continue;
			}
			JSONObject contentObj = content.getJSONObject(0);
			if (!contentObj.has("url")) {
				continue;
			}
			String downloadUrl = contentObj.getString("url");
			System.out.println("download url " + downloadUrl);
			ret.add(downloadUrl);
		}
		return ret;
	}
	
	private boolean downloadImage(String strUrl, File f) throws IOException {
		URL url = new URL(strUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.connect();
		
		InputStream in = null;
		try {
			in = conn.getInputStream();
			if (f.exists()) {
				f.delete();
			}
			return streamToFile(in, f);
		} finally {
			close(in);
		}
	}
	
	private Bitmap decode(String path) {
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, opts);
		System.out.println("image width " + opts.outWidth + ", height " + opts.outHeight);
		
		int baseLength = (opts.outWidth > opts.outHeight) ? opts.outHeight : opts.outWidth;
		int targetSize = 640;
		
		float scale = (float)baseLength / targetSize;
		scale = (scale <= 0.f) ? 1.f : scale;
		
		System.out.println("scale " + scale);
		
		opts.inJustDecodeBounds = false;
		opts.inSampleSize = (int) scale;
		
		Bitmap b = BitmapFactory.decodeFile(path, opts);
		if (b != null) {
			System.out.println("decoded bitmap w " + b.getWidth() + ", h " + b.getHeight());
		}
		return b;
	}
	
	private String streamToString(InputStream in) {
		try {
			InputStreamReader is = new InputStreamReader(in);
			StringBuilder sb = new StringBuilder();
			BufferedReader br = new BufferedReader(is);
			String read = br.readLine();
	
			while(read != null) {
			    sb.append(read);
			    read = br.readLine();
			}
			
			return sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private boolean streamToFile(InputStream in, File outputFilePath) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(outputFilePath);
			byte[] buffer = new byte[2048];
			int count;
			while ((count = in.read(buffer)) > 0) {
				out.write(buffer, 0, count);
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			close(out);
		}
		return false;
	}
	
	public interface OnImageSearchCompleteListener {
		public void onComplete(Bitmap b);
		public void onError();
	}
	
}
