package me.kuku.simbot.logic.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import me.kuku.pojo.Result;
import me.kuku.pojo.UA;
import me.kuku.simbot.entity.HeyTapEntity;
import me.kuku.simbot.logic.HeyTapLogic;
import me.kuku.simbot.pojo.HeyTapQrcode;
import me.kuku.utils.*;
import okhttp3.Response;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Lazy
public class HeyTapLogicImpl implements HeyTapLogic {

	private final String SIGN_KEY = "&key=FdjydGAAKasmht1nFnR4MS5itFeh4R1Lk";
	private final String RSA_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCf5viGpYn1duRt9wzwca1SEuL+wwnBfBfza0nTuLPYR5uZyheUoFI+cudN9eB4jlvXij4yAxH59ML8BhVUab/j+TmeDsCe+OLpswdHWEXtY1HacLpw/wpsKQHBQZYhAARZRx/4J5/fiz/pJcH5qVGYK0Yu8c9CNl9/eHDQkj9LoQIDAQAB";

	private final String AES_KEY;
	private String ticket = null;

	public HeyTapLogicImpl(){
		this.AES_KEY = MyUtils.random(16);
		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("callback", "https://www.heytap.com/cn/web/");
			Response response = request("https://id.heytap.com/api/login/v1/auth", jsonObject);
			response.close();
			ticket = response.header("X-Session-Ticket");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private Response request(String url, JSONObject params) throws IOException {
		params.put("appKey", "CuGsbe6HdAe6vDBHFew2Di");
		long time = System.currentTimeMillis();
		params.put("nonce", String.valueOf(time));
		params.put("timestamp", time);
		List<String> signList = new ArrayList<>();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			Object value = entry.getValue();
			if ("".equals(value)) continue;
			if (value instanceof String || value instanceof Long){
				signList.add(entry.getKey() + "=" + value);
			}
		}
		Collections.sort(signList);
		String sign = StringUtils.join(signList, '&');
		sign += SIGN_KEY;
		params.put("sign", MD5Utils.toMD5(sign));
		Map<String, String> headers = new HashMap<>();
		headers.put("Accept", "application/encrypted-json;charset=UTF-8");
		headers.put("referer", "https://id.heytap.com/");
		headers.put("Origin", "https://id.heytap.com/");
		headers.put("user-agent", UA.PC.getValue());
		headers.put("X-BusinessSystem", "HeyTap");
		headers.put("X-From-HT", "true");
		headers.put("X-Protocol-Version", "1.0");
		headers.put("X-Timezone", "GMT+8");
		headers.put("fromPackageName", "");
		try {
			headers.put("X-Key", RSAUtils.encrypt(AES_KEY, RSAUtils.getPublicKey(RSA_KEY)));
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (ticket != null)
			headers.put("X-Session-Ticket", ticket);
		return OkHttpUtils.post(url,
				OkHttpUtils.addEncryptedJson(AESUtils.aesEncryptBase(AES_KEY, params.toJSONString())),
				OkHttpUtils.addHeaders(headers));
	}

	private JSONObject requestJson(String url, JSONObject params) throws IOException {
		Response response = request(url, params);
		return requestJson(response);
	}

	private JSONObject requestJson(Response response) throws IOException {
		String str = OkHttpUtils.getStr(response);
		byte[] bytes = AESUtils.aesDecrypt(AES_KEY.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(str));
		String jsonStr = new String(bytes);
		return JSON.parseObject(jsonStr);
	}

	@Override
	public HeyTapQrcode getQrcode() throws IOException {
		String deviceId = MyUtils.randomStr(32);
		JSONObject params = new JSONObject();
		params.put("appId", "usercenter-web");
		params.put("context", new JSONObject(){{
			put("deviceId", deviceId);
		}});
		JSONObject jsonObject = requestJson("https://id.heytap.com/api/scan-login/v1/general-qrcode", params);
		JSONObject dataJsonObject = jsonObject.getJSONObject("data");
		String qid = dataJsonObject.getString("qid");
		String url = dataJsonObject.getString("qrcodeUrl");
		return new HeyTapQrcode(url, deviceId, qid);
	}

	@Override
	public Result<HeyTapEntity> checkQrcode(HeyTapQrcode heyTapQrcode) throws IOException {
		//  getJSONObject("error").getInteger("code")
		// 2310501  未扫描
		// 2310503 已失效
		// 2310502 已扫描
		//
		JSONObject params = new JSONObject();
		params.put("qid", heyTapQrcode.getQid());
		params.put("deviceId", heyTapQrcode.getDeviceId());
		params.put("callbackUrl", "https://www.heytap.com/cn/web/");
		Response response = request("https://id.heytap.com/api/scan-login/v1/check-qrcode", params);
		JSONObject jsonObject = requestJson(response);
		if (jsonObject.getBoolean("success")){
			String cookie = OkHttpUtils.getCookie(response);
			HeyTapEntity entity = new HeyTapEntity();
			entity.setCookie(cookie);
			Map<String, String> headers = new HashMap<>();
			headers.put("Referer", "https://id.heytap.com/");
			headers.put("Cookie", cookie);
			headers.put("Origin", "https://id.heytap.com/");
			headers.put("user-agent", UA.PC.getValue());
			headers.put("Accept", "*/*");
			headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
			headers.put("Pragma", "no-cache");
			headers.put("Connection", "keep-alive");
			headers.put("Content-Length", "35");
			headers.put("Cache-Control", "no-cache");
			headers.put("Accept-Encoding", "gzip, deflate, br");
			headers.put("Accept-Language", "zh,zh-CN;q=0.9,en;q=0.8");
			JSONArray urlsJSONArray = jsonObject.getJSONObject("data").getJSONArray("encryptSessions");
			for (Object o : urlsJSONArray) {
				String longUrl = o.toString();
				int index = longUrl.indexOf("=");
				String url = longUrl.substring(0, index - 4);
				String host = MyUtils.regex("https://", "/", url);
				headers.put("Host", host);
				if ("https://msec.heytap.com/security/web/login/setCookies".equals(url)) {
					String key = longUrl.substring(index + 1);
					try {
						Response resp = OkHttpUtils.post(url, new HashMap<String, String>() {{
							put("key", key);
						}}, headers);
						resp.close();
						entity.setHeyTapCookie(OkHttpUtils.getCookie(resp));
					} catch (IOException ignore) {
					}
				}
			}
			return Result.success(entity);
		}else {
			Integer code = jsonObject.getJSONObject("error").getInteger("code");
			if (code == 2310501 || code == 2310502) return Result.failure(0, "已扫描或未扫描");
			else if (code == 2310503) return Result.failure("已失效");
			else return Result.failure("未知的代码");
		}
	}

	private JSONObject taskCenter(HeyTapEntity heyTapEntity) throws IOException {
		return OkHttpUtils.getJson("https://store.oppo.com/cn/oapi/credits/web/credits/show",
				OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "https://store.oppo.com/cn/app/taskCenter/index",
						UA.OPPO));
	}

	@Override
	public Result<Void> sign(HeyTapEntity heyTapEntity) throws IOException {
		JSONObject jsonObject = taskCenter(heyTapEntity);
		if (jsonObject.getInteger("code") != 200) return Result.failure(jsonObject.getString("errorMessage"));
		JSONObject dataJsonObject = jsonObject.getJSONObject("data");
		if (dataJsonObject.getJSONObject("userReportInfoForm").getInteger("status") == 0){
			JSONArray jsonArray = dataJsonObject.getJSONObject("userReportInfoForm").getJSONArray("gifts");
			String now = DateTimeFormatterUtils.formatNow("yyyy-MM-dd");
			JSONObject qd = null;
			for (Object o : jsonArray) {
				JSONObject it = (JSONObject) o;
				if (it.getString("date").equals(now)) {
					qd = it;
					break;
				}
			}
			assert qd != null;
			Map<String, String> params = new HashMap<>();
			params.put("amount", qd.getString("credits"));
			if (Boolean.TRUE.equals(qd.getBoolean("today"))){
				if (qd.getString("type").length() != 0){
					params.put("amout", qd.getString("credits"));
					params.put("type", qd.getString("type"));
					params.put("gift", qd.getString("gift"));
				}
			}
			JSONObject resultJsonObject = OkHttpUtils.postJson("https://store.oppo.com/cn/oapi/credits/web/report/immediately", params,
					OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "https://store.oppo.com/cn/app/taskCenter/index",
							UA.OPPO));
			if (resultJsonObject.getInteger("code") == 200) return Result.success("签到成功", null);
			else return Result.failure("签到失败，" + resultJsonObject.getString("errorMessage"));
		}else return Result.failure("今日已签到！");
	}

	private boolean cashingCredits(HeyTapEntity heyTapEntity, String infoMarking, String infoType, String infoCredits) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("marking", infoMarking);
		params.put("type", infoType);
		params.put("amount", infoCredits);
		Response response = OkHttpUtils.post("https://store.oppo.com/cn/oapi/credits/web/credits/cashingCredits", params,
				OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "https://store.oppo.com/cn/app/taskCenter/index?us=gerenzhongxin&um=hudongleyuan&uc=renwuzhongxin", UA.OPPO));
		response.close();
		return response.code() == 200;
	}

	@Override
	public Result<Void> viewGoods(HeyTapEntity heyTapEntity) throws IOException {
		JSONObject jsonObject = taskCenter(heyTapEntity);
		JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("everydayList");
		JSONObject qd = null;
		for (Object o : jsonArray) {
			JSONObject it = (JSONObject) o;
			if (it.getString("name").equals("浏览商品")){
				qd = it;
				break;
			}
		}
		assert qd != null;
		Integer status = qd.getInteger("completeStatus");
		if (status == 0){
			JSONObject shopJsonObject = OkHttpUtils.getJson("https://msec.opposhop.cn/goods/v1/SeckillRound/goods/3016?pageSize=12&currentPage=1");
			if (shopJsonObject.getJSONObject("meta").getInteger("code") == 200){
				for (Object o : shopJsonObject.getJSONArray("detail")) {
					JSONObject it = (JSONObject) o;
					String skuId = it.getString("skuid");
					OkHttpUtils.get("https://msec.opposhop.cn/goods/v1/info/sku?skuId=" + skuId,
							OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "", UA.OPPO)).close();
					try {
						TimeUnit.SECONDS.sleep(3);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

			}else return Result.failure("每日浏览商品失败，获取商品列表失败");
		}else if (status == 2)
			return Result.success("每日浏览商品已完成", null);
		boolean b = cashingCredits(heyTapEntity, qd.getString("marking"), qd.getString("type"), qd.getString("credits"));
		if (b)
			return Result.success("每日浏览商品成功", null);
		else return Result.failure("每日浏览商品失败！");
	}

	@Override
	public Result<Void> shareGoods(HeyTapEntity heyTapEntity) throws IOException {
		JSONObject jsonObject = taskCenter(heyTapEntity);
		JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("everydayList");
		JSONObject qd = null;
		for (Object o : jsonArray) {
			JSONObject it = (JSONObject) o;
			if (it.getString("name").equals("分享商品到微信")){
				qd = it;
				break;
			}
		}
		assert qd != null;
		Integer status = qd.getInteger("completeStatus");
		if (status == 0){
			Integer readCount = qd.getInteger("readCount");
			Integer endCount = qd.getInteger("times");
			while (readCount < endCount){
				OkHttpUtils.get("https://msec.opposhop.cn/users/vi/creditsTask/pushTask?marking=daily_sharegoods",
						OkHttpUtils.addCookie(heyTapEntity.getHeyTapCookie())).close();
				readCount++;
			}
		}else if (status == 2)
			return Result.success("每日分享商品已完成", null);
		boolean b = cashingCredits(heyTapEntity, qd.getString("marking"), qd.getString("type"), qd.getString("credits"));
		if (b)
			return Result.success("每日分享商品成功", null);
		else return Result.failure("每日分享商品失败！");
	}

	@Override
	public Result<Void> viewPush(HeyTapEntity heyTapEntity) throws IOException {
		JSONObject jsonObject = taskCenter(heyTapEntity);
		JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("everydayList");
		JSONObject qd = null;
		for (Object o : jsonArray) {
			JSONObject it = (JSONObject) o;
			if (it.getString("name").equals("点推送消息")){
				qd = it;
				break;
			}
		}
		assert qd != null;
		Integer status = qd.getInteger("completeStatus");
		if (status == 0){
			Integer readCount = qd.getInteger("readCount");
			Integer endCount = qd.getInteger("times");
			while (readCount < endCount){
				OkHttpUtils.get("https://msec.opposhop.cn/users/vi/creditsTask/pushTask?marking=daily_viewpush",
						OkHttpUtils.addCookie(heyTapEntity.getHeyTapCookie())).close();
				readCount++;
			}
		}else if (status == 2)
			return Result.success("每日点推送已完成", null);
		boolean b = cashingCredits(heyTapEntity, qd.getString("marking"), qd.getString("type"), qd.getString("credits"));
		if (b)
			return Result.success("每日点推送成功", null);
		else return Result.failure("每日点推送失败！");
	}

	@Override
	public Result<Void> earlyBedRegistration(HeyTapEntity heyTapEntity) throws IOException {
		JSONObject jsonObject = OkHttpUtils.getJson("https://store.oppo.com/cn/oapi/credits/web/clockin/applyOrClockIn",
				OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "https://store.oppo.com/cn/app/cardingActivities?us=gerenzhongxin&um=hudongleyuan&uc=zaoshuidaka",
						UA.OPPO));
		if (jsonObject.getInteger("code") == 200) return Result.success("报名成功", null);
		else return Result.failure("报名失败：" + jsonObject.getString("errorMessage"));
	}

	@Override
	public Result<Void> earlyBedPunchCard(HeyTapEntity heyTapEntity) {
		return null;
	}

	private JSONObject lottery(HeyTapEntity heyTapEntity, Map<String, String> params) throws IOException {
		params.put("mobile", "");
		params.put("authcode", "");
		params.put("captcha", "");
		params.put("isCheck", "0");
		params.put("source_type", "501");
		params.put("s_channel", "oppo_appstore");
		params.put("sku", "");
		params.put("spu", "");
		Map<String, String> headers = new HashMap<>();
		headers.put("cookie", heyTapEntity.getHeyTapCookie());
		headers.put("clientPackage", "com.oppo.store");
		headers.put("Origin", "https://hd.oppo.com");
		headers.put("user-agent", UA.OPPO.getValue());
		return OkHttpUtils.postJson("https://hd.oppo.com/platform/lottery", params, headers);
	}

	private JSONObject taskFinish(HeyTapEntity heyTapEntity, String aid, String tIndex) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("aid", aid);
		params.put("t_index", tIndex);
		return OkHttpUtils.postJson("https://hd.oppo.com/task/finish", params,
				OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "", UA.OPPO));
	}

	private JSONObject taskAward(HeyTapEntity heyTapEntity, String aid, String tIndex) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("aid", aid);
		params.put("t_index", tIndex);
		return OkHttpUtils.postJson("https://hd.oppo.com/task/award", params,
				OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "", UA.OPPO));
	}

	@Override
	public Result<Void> pointsEveryDay(HeyTapEntity heyTapEntity) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("aid", "675");
		params.put("lid", "1289");
		for (int i = 0; i < 3; i++){
			JSONObject jsonObject = lottery(heyTapEntity, params);
			if (jsonObject.getInteger("no") != 0)
				return Result.failure(jsonObject.getString("msg"));
		}
		return Result.success("天天积分翻倍成功！", null);
	}

	@Override
	public Result<Void> transferPoints(HeyTapEntity heyTapEntity) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("aid", "1418");
		params.put("lid", "1307");
		JSONObject jsonObject = lottery(heyTapEntity, params);
		if (jsonObject.getInteger("no") != 0)
			return Result.failure(jsonObject.getString("msg"));
		JSONObject listJsonObject = OkHttpUtils.getJson("https://hd.oppo.com/task/list?aid=1418",
				OkHttpUtils.addHeaders(heyTapEntity.getHeyTapCookie(), "https://hd.oppo.com/act/m/2021/jifenzhuanpan/index.html?us=gerenzhongxin&um=hudongleyuan&uc=yingjifen",
						UA.OPPO));
		JSONArray dataJsonArray = listJsonObject.getJSONArray("data");
		for (Object o : dataJsonArray) {
			JSONObject jobs = (JSONObject) o;
			Integer status = jobs.getInteger("t_status");
			JSONObject finishJsonObject = null;
			if (status == 0){
				String index = jobs.getString("t_index");
				String aid = index.substring(0, index.indexOf('i'));
				finishJsonObject = taskFinish(heyTapEntity, aid, index);

			}else if (status == 1){
				String index = jobs.getString("t_index");
				String aid = index.substring(0, index.indexOf('i'));
				finishJsonObject = taskAward(heyTapEntity, aid, index);
			}
			if (finishJsonObject != null){
				if (finishJsonObject.getInteger("no") != 200)
					return Result.failure(jsonObject.getString("msg"));
				try {
					TimeUnit.SECONDS.sleep(3);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				JSONObject lotteryJsonObject = lottery(heyTapEntity, params);
				if (lotteryJsonObject.getInteger("no") != 0)
					return Result.failure(lotteryJsonObject.getString("msg"));
			}
		}
		return Result.success("赚积分-天天抽奖成功！", null);
	}

	@Override
	public Result<Void> smartLifeLottery(HeyTapEntity heyTapEntity) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("aid", "1270");
		params.put("lid", "1431");
		for (int i = 0; i < 5; i++){
			JSONObject jsonObject = lottery(heyTapEntity, params);
			if (jsonObject.getInteger("no") != 0)
				return Result.failure(jsonObject.getString("msg"));
		}
		return Result.success("智能生活转盘抽奖成功！", null);
	}

	@Override
	public Result<Void> petFanPlan(HeyTapEntity heyTapEntity) throws IOException {
		Map<String, String> params = new HashMap<>();
		params.put("aid", "1182");
		params.put("lid", "1429");
		for (int i = 0; i < 5; i++){
			JSONObject jsonObject = lottery(heyTapEntity, params);
			if (jsonObject.getInteger("no") != 0)
				return Result.failure(jsonObject.getString("msg"));
		}
		return Result.success("智能生活转盘抽奖成功！", null);
	}
}