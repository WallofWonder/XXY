package com.yhx.app.common;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue; 
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity; 
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import com.yhx.app.campus_life.ShopInfoActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;


public final class HttpHelper {
	private static final String TAG = "HttpHelper";
	/** HttpClient????(???????) */
	private static HttpClient httpClient;
	/** Android???????? */
	private static String appUserAgent;
	/** ??????? */
	private final static int RETRY_TIME = 3;
	/** ???????????????? */
	private final static long SLEEP_TIME = 500;

	private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
			5, 128, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(10));

	static {
		if (null == httpClient) {
			// httpClient = new DefaultHttpClient();

			// ////////////////???????????????HttpClient???????????????????????????????
			HttpParams httpParams = new BasicHttpParams();

			HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(httpParams, HTTP.UTF_8);
			HttpProtocolParams.setUseExpectContinue(httpParams, true);

			// ?????????????
			ConnManagerParams.setMaxTotalConnections(httpParams, 10);
			// ???????????????????
			ConnManagerParams.setTimeout(httpParams, 50000);
			// ???????????????????
			ConnManagerParams.setMaxConnectionsPerRoute(httpParams,
					new ConnPerRouteBean(8));
			// ?????????????
			HttpConnectionParams.setConnectionTimeout(httpParams, 20000);
			// ????????????
			HttpConnectionParams.setSoTimeout(httpParams, 20000);

			// ??????? Cookie?????,?????????????????
			HttpClientParams.setCookiePolicy(httpParams,
					CookiePolicy.BROWSER_COMPATIBILITY);

			SchemeRegistry schreg = new SchemeRegistry();
			schreg.register(new Scheme("http", PlainSocketFactory
					.getSocketFactory(), 80));
			schreg.register(new Scheme("https", SSLSocketFactory
					.getSocketFactory(), 443));

			ClientConnectionManager connManager = new ThreadSafeClientConnManager(
					httpParams, schreg);

			httpClient = new DefaultHttpClient(connManager, httpParams);
		}
	}
	/**
	 * ????HTTP?????????????
	 * 
	 * @author csdn
	 */
	public static interface Callback {
		/**
		 * HTTP?????????????
		 * 
		 * @param data
		 *            ???????????????
		 */
		public void dataLoaded(Message msg);
	}

	private HttpHelper() {
	}

	public static HttpClient getHttpClient() {
		return httpClient;
	}

	private static String getUserAgent() {
		if (appUserAgent == null || appUserAgent == "") {
			StringBuilder sb = new StringBuilder("qiujy");
			sb.append("/Android");// ???????
			sb.append("/" + android.os.Build.VERSION.RELEASE);// ??????��
			sb.append("/" + android.os.Build.MODEL); // ??????
			appUserAgent = sb.toString();
		}
		return appUserAgent;
	}

	private static HttpGet getHttpGet(String url) {
		Log.d(TAG, "url-->" + url);
		HttpGet httpGet = new HttpGet(url);
		httpGet.setHeader("Connection", "Keep-Alive");
		// httpGet.setHeader("Cookie", cookie);
		httpGet.setHeader("User-Agent", getUserAgent());
		// httpGet.addHeader("Accept-Encoding", "gzip");

		return httpGet;
	}

	private static HttpPost getHttpPost(String url) {
		Log.d(TAG, "url-->" + url);
		HttpPost httpPost = new HttpPost(url);
		httpPost.setHeader("Connection", "Keep-Alive");
		// httpPost.setHeader("Cookie", cookie);
		httpPost.setHeader("User-Agent", getUserAgent());
		// httpPost.addHeader("Accept-Encoding", "gzip");

		return httpPost;
	}

	/**
	 * ???????
	 * 
	 * @param url
	 *            ????URL
	 * @param dest
	 *            ??????????
	 * @throws IOException
	 */
	public static void download(String url, File dest) throws AppException {
		HttpEntity entity = retry(getHttpGet(url));

		if (entity != null) {
			InputStream bis = null;
			BufferedOutputStream bos = null;
			byte[] b = new byte[4096];
			try {
				bis = entity.getContent();
				bos = new BufferedOutputStream(new FileOutputStream(dest));
				for (int count = -1; (count = bis.read(b)) != -1;) {
					bos.write(b, 0, count);
				}
				bos.flush();
			} catch (IOException e) {
				e.printStackTrace();
				throw AppException.io(e);
			} finally {
				try {
					entity.consumeContent();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				if (bis != null) {
					try {
						bis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (bos != null) {
					try {
						bos.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * ??????????????????????????????callback.dataLoaded(Message msg)????<br/>
	 * 1) ??????????: msg.what = 200; ????????????: String str = (String)msg.obj<br/>
	 * 2) ??????????: msg.what = 500; ?????????????: AppException e =
	 * (AppException)msg.obj<br/>
	 * 
	 * @param url
	 * @param dest
	 * @param callback
	 */
	public static void asyncDownload(final String url, final File dest,
			final Callback callback) {
		final Handler handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				callback.dataLoaded(msg);
			}
		};

		threadPoolExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Message msg = handler.obtainMessage(HttpStatus.SC_OK);
				try {
					download(url, dest);
				} catch (AppException e) {
					e.printStackTrace();

					msg.what = HttpStatus.SC_INTERNAL_SERVER_ERROR;
					msg.obj = e;
				}

				handler.sendMessage(msg);
			}
		});
	}
	//??????
	public static void downloadBitmap(final String url, final Callback callback) {
		final Handler handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				callback.dataLoaded(msg);
			}
		};

		threadPoolExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Message msg = handler.obtainMessage(HttpStatus.SC_OK);
				try {
					HttpClient httpClient = HttpHelper.getHttpClient();
					HttpResponse response = httpClient.execute(new HttpGet(url));
					if (response.getStatusLine().getStatusCode() == 200) {
						InputStream is = (InputStream) response.getEntity()
								.getContent();
						BitmapFactory.Options opts = new BitmapFactory.Options();
						opts.inSampleSize = 4;
						Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
						msg.obj = bitmap;
						is.close();
					} else {
						
					}
				} catch (Exception e) {
					msg.what = HttpStatus.SC_INTERNAL_SERVER_ERROR;
					msg.obj = e;
					e.printStackTrace();
				}
				handler.sendMessage(msg);
			}
		});
	}

	public static String get(String url) throws AppException {
		String str = null;
		HttpEntity entity = retry(getHttpGet(url));
		if (entity != null) {
			try {
				str = EntityUtils.toString(entity);
			} catch (ParseException e) {
				e.printStackTrace();
				throw AppException.dataParser(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw AppException.dataParser(e);
			}
		}
		return str;
	}

	/**
	 * ???????GET???????????????callback.dataLoaded(Message msg)????<br/>
	 * 1) ??????????: msg.what = 200; ????????????: String str = (String)msg.obj<br/>
	 * 2) ??????????: msg.what = 500; ?????????????: AppException e =
	 * (AppException)msg.obj<br/>
	 * 
	 * @param url
	 * @param callback
	 */
	public static void asyncGet(final String url, final Callback callback) {
		final Handler handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				callback.dataLoaded(msg);
			}
		};

		threadPoolExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Message msg = handler.obtainMessage(HttpStatus.SC_OK);
				try {
					String str = get(url);
					msg.obj = str;
				} catch (AppException e) {
					e.printStackTrace();

					msg.what = HttpStatus.SC_INTERNAL_SERVER_ERROR;
					msg.obj = e;
				}

				handler.sendMessage(msg);
			}
		});
	}

	/**
	 * ????????POST?????????????????????????????????????
	 * 
	 * @param url
	 *            ????URL
	 * @return ??????????????????
	 * @throws AppException
	 */
	public static String post(String url, HashMap<String, Object> params)
			throws AppException {
		HttpPost post = getHttpPost(url);
		if (null != params) {
			List<NameValuePair> pairList = new ArrayList<NameValuePair>();
			for (Entry<String, Object> paramPair : params.entrySet()) {
				NameValuePair pair = new BasicNameValuePair(paramPair.getKey(),
						String.valueOf(paramPair.getValue()));
				pairList.add(pair);
			}
			try {
				HttpEntity entity = new UrlEncodedFormEntity(pairList,
						HTTP.UTF_8);
				post.setEntity(entity);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

		HttpEntity entity = retry(post);
		String str = null;
		if (entity != null) {
			try {
				str = EntityUtils.toString(entity);
			} catch (ParseException e) {
				e.printStackTrace();
				throw AppException.dataParser(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw AppException.dataParser(e);
			}
		}
		return str;
	}

	/**
	 * ???????POST???????????????callback.dataLoaded(Message msg)????<br/>
	 * 1) ??????????: msg.what = 200; ????????????: String str = (String)msg.obj<br/>
	 * 2) ??????????: msg.what = 500; ?????????????: AppException e =
	 * (AppException)msg.obj<br/>
	 * 
	 * @param url
	 * @param callback
	 */
	public static void asyncPost(final String url,
			final HashMap<String, Object> params, final Callback callback) {
		final Handler handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				callback.dataLoaded(msg);
			}
		};

		threadPoolExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Message msg = handler.obtainMessage(HttpStatus.SC_OK);
				try {
					String str = post(url, params);
					msg.obj = str;
				} catch (AppException e) {
					e.printStackTrace();

					msg.what = HttpStatus.SC_INTERNAL_SERVER_ERROR;
					msg.obj = e;
				}

				handler.sendMessage(msg);
			}
		});
	}

	/**
	 * ????????POST????????????multipart/form-data???????????????��??????????
	 * 
	 * @param url
	 *            ????URL
	 * @param params
	 *            ????????????Map
	 * @param fileMap
	 *            ??????????????Map
	 * @return ??????????????????
	 * @throws AppException
	 */
	public static String multipartPost(String url,
			HashMap<String, Object> params, HashMap<String, File> fileMap)
			throws AppException {
		HttpPost post = getHttpPost(url);

		MultipartEntity entity = new MultipartEntity(
				HttpMultipartMode.BROWSER_COMPATIBLE, null,
				Charset.forName("UTF-8"));

		// httpmime4.3????
		// MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		// builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		// builder.setCharset(Charset.forName(HTTP.UTF_8));

		// ???????????
		if (null != params) {
			for (Entry<String, Object> paramPair : params.entrySet()) {
				try {
					entity.addPart(paramPair.getKey(),
							new StringBody(
									String.valueOf(paramPair.getValue()),
									Charset.forName("UTF-8")));
					Log.v("dddddddddddddddddddddd", paramPair.getKey() + ":"
							+ String.valueOf(paramPair.getValue()));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();

				}
				// builder.addTextBody(paramPair.getKey(),
				// String.valueOf(paramPair.getValue()));
			}
		}
		// ???????????
		if (null != fileMap) {
			for (Entry<String, File> paramPair : fileMap.entrySet()) {
				entity.addPart(paramPair.getKey(),
						new FileBody(paramPair.getValue()));
				Log.v("dddddddddddddddddddddd", paramPair.getKey() + ":"
						+ String.valueOf(paramPair.getValue()));
				// builder.addBinaryBody(paramPair.getKey(),
				// paramPair.getValue());
			}
		}
		post.setEntity(entity);

		HttpEntity result_entity = retry(post);
		String str = null;
		if (result_entity != null) {
			try {
				str = EntityUtils.toString(result_entity);
			} catch (ParseException e) {
				e.printStackTrace();
				throw AppException.dataParser(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw AppException.dataParser(e);
			}
		}
		return str;
	}

	/**
	 * ???????POST????????????multipart/form-data???????????????��??????????<br/>
	 * 1) ??????????: msg.what = 200; ????????????: String str = (String)msg.obj<br/>
	 * 2) ??????????: msg.what = 500; ?????????????: AppException e =
	 * (AppException)msg.obj<br/>
	 * 
	 * @param url
	 *            ????URL
	 * @param params
	 *            ????????????Map
	 * @param fileMap
	 *            ??????????????Map
	 * @param callback
	 */
	public static void asyncMultipartPost(final String url,
			final HashMap<String, Object> params,
			final HashMap<String, File> fileMap, final Callback callback) {
		final Handler handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				callback.dataLoaded(msg);
			}
		};

		threadPoolExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Message msg = handler.obtainMessage(HttpStatus.SC_OK);
				try {
					String str = multipartPost(url, params, fileMap);
					msg.obj = str;
				} catch (AppException e) {
					e.printStackTrace();

					msg.what = HttpStatus.SC_INTERNAL_SERVER_ERROR;
					msg.obj = e;
				}

				handler.sendMessage(msg);
			}
		});

	}

	/**
	 * ????HTTP?????????????HttpEntity????????????
	 * 
	 * @param req
	 *            ????????????HttpGet??HttpPost
	 * @return HttpEntity ???????
	 * @throws AppException
	 *             ??��?????????
	 */
	private static HttpEntity retry(HttpRequestBase req) throws AppException {
		HttpEntity result = null;
		int count = 0;
		while (count++ < RETRY_TIME) {
			try {
				Log.d(TAG, "??" + count + "?��???????????...");
				// String str = req.getFirstHeader("User-Agent").toString();
				// Log.d(TAG, str);

				HttpResponse response = httpClient.execute(req);
				int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK) {
					throw AppException.http(statusCode);
				} else {
					result = response.getEntity();
				}
				break;
			} catch (ClientProtocolException e) {
				e.printStackTrace();

				if (count < RETRY_TIME) {
					try {
						Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e1) {
					}
				} else {
					throw AppException.http(e);
				}
			} catch (IOException e) {
				e.printStackTrace();
				if (count < RETRY_TIME) {
					try {
						Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e1) {
					}
				} else {
					throw AppException.network(e);
				}
			}
		}

		return result;
	}

	// ?��???????????
	public static boolean IsHaveInternet(final Context context) {
		try {
			ConnectivityManager manger = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);

			NetworkInfo info = manger.getActiveNetworkInfo();
			return (info != null && info.isConnected());
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * An InputStream that skips the exact number of bytes provided, unless it
	 * reaches EOF. private static class FlushedInputStream extends
	 * FilterInputStream { public FlushedInputStream(InputStream inputStream) {
	 * super(inputStream); }
	 * 
	 * @Override public long skip(long n) throws IOException { long
	 *           totalBytesSkipped = 0L; while (totalBytesSkipped < n) { long
	 *           bytesSkipped = in.skip(n - totalBytesSkipped); if (bytesSkipped
	 *           == 0L) { int bytes = read(); if (bytes < 0) { break; // ???????????
	 *           } else { bytesSkipped = 1; // ???????? } } totalBytesSkipped +=
	 *           bytesSkipped; } return totalBytesSkipped; } }
	 */
}