package com.xiaoji.duan.mpp;

import io.vertx.amqpbridge.AmqpBridge;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * 
 * 向微信公众号关注用户发送指定消息
 * 
 * @author xiaoji
 *
 */
public class MainVerticle extends AbstractVerticle {

	private WebClient client = null;
	private AmqpBridge bridge = null;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		client = WebClient.create(vertx);

		bridge = AmqpBridge.create(vertx);

		bridge.endHandler(handler -> {
			connectStompServer();
		});

		connectStompServer();

	}

	private void connectStompServer() {
		bridge.start(config().getString("stomp.server.host", "sa-amq"), config().getInteger("stomp.server.port", 5672),
				res -> {
					if (res.failed()) {
						res.cause().printStackTrace();
						connectStompServer();
					} else {
						subscribeTrigger(config().getString("amq.app.id", "mpp"));
					}
				});

	}

	private void subscribeTrigger(String trigger) {
		MessageConsumer<JsonObject> consumer = bridge.createConsumer(trigger);
		System.out.println("Consumer " + trigger + " subscribed.");
		consumer.handler(vertxMsg -> this.process(trigger, vertxMsg));
	}

	private void process(String consumer, Message<JsonObject> received) {
		System.out.println("Consumer " + consumer + " received [" + received.body().encode() + "]");
		JsonObject data = received.body().getJsonObject("body");
		
		JsonObject context = data.getJsonObject("context", new JsonObject());
		
		String messageType = context.getString("messageType");
		String sendFrom = context.getString("sendFrom");
		String sendTo = context.getString("sendTo");
		JsonObject content = context.getJsonObject("content");
		
		JsonObject message = new JsonObject();
		message.put("toUser", sendTo)
		.put("msgType", "text")
		.put("text", new JsonObject().put("content", content.encode()));
		
		Future<JsonObject> accessFuture = Future.future();
		
		accessFuture.setHandler(handler -> {
			if (handler.succeeded()) {
				JsonObject accessToken = handler.result();
				
				sendMessage(accessToken, message);
			} else {
				handler.cause().printStackTrace();
			}
		});
		
		getAccessToken(accessFuture);
	}
	
	private void sendMessage(JsonObject accesstoken, JsonObject message) {
		StringBuffer url = new StringBuffer(config().getString("openwxapi.message.send", "https://api.weixin.qq.com/cgi-bin/message/custom/send"));
		url.append("?access_token=");
		url.append(accesstoken.getString("access_token"));
		
		client.postAbs(url.toString()).sendJsonObject(message, handler -> {
			if (handler.succeeded()) {
				HttpResponse<Buffer> resp = handler.result();

				String result = resp.bodyAsString();
				
				System.out.println(result);
			} else {
				handler.cause().printStackTrace();
			}
		});
	}
	
	private void getAccessToken(Future<JsonObject> future) {
		StringBuffer url = new StringBuffer(config().getString("openwxapi.accesstoken", "https://api.weixin.qq.com/cgi-bin/token"));
		url.append("?grant_type=");
		url.append("client_credential");
		url.append("&appid=");
		url.append(config().getString("openwxapi.appid", "wxd8e2dbb017af8b49"));
		url.append("&secret=");
		url.append(config().getString("openwxapi.secret", "50ab6ee019c366b340e7a1f1210b26b3"));
		
		client.getAbs(url.toString()).send(handler -> {
			if (handler.succeeded()) {
				HttpResponse<Buffer> resp = handler.result();
				
				System.out.println(resp.bodyAsString());
				JsonObject accessToken = resp.bodyAsJsonObject();

				future.complete(accessToken);
			} else {
				future.fail(handler.cause());
			}
		});
	}
}
