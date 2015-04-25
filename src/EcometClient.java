import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;


public class EcometClient extends WebSocketClient {

    private final AtomicInteger ref = new AtomicInteger(1);
    private final CountDownLatch logonWaiter = new CountDownLatch(1);
    private final Map<Integer, Request> requestMap = new HashMap<>();
    private final String login;
    private final String pass;
    private boolean loggedIn = false;

//    public EcometClient(URI serverUri, Draft draft) {
//        super(serverUri, draft);
//    }

    public EcometClient(URI serverURI, String login, String pass) {
        super(serverURI);
        this.login = login;
        this.pass = pass;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("opened connection");
        Request loginReq = new Request("login", nextRef());
        loginReq.addParam("login", login);
        loginReq.addParam("pass", pass);
        send(loginReq);
    }

    @Override
    public void onMessage(String message) {
        System.out.println("received: " + message);
        Map<String, String> resp = parseJsonPlain(message);
        if (resp != null) {
            int ref = Integer.parseInt(resp.get("id"));
            String type = resp.get("type");
            Request req = requestMap.get(ref);
            if (req != null) {
                if (req.type.equals("login") && !loggedIn) {
                    if (type.equals("ok")) {
                        loggedIn = true;
                    }
                    logonWaiter.countDown();
                }
            }

        }
    }


    @Override
    public void onClose(int code, String reason, boolean remote) {
        // The codecodes are documented in class org.java_websocket.framing.CloseFrame
        System.out.println("Connection closed by " + (remote ? "remote peer" : "us"));
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        // if the error is fatal then onClose will be called additionally
    }


    public void query(String query) {
        Request req = new Request("query", nextRef());
        req.addParam("query_string", query);
        send(req);
    }

    public void send(Request req) {
        requestMap.put(req.ref, req);
        send(req.toJson());
    }

    public boolean waitForLogon(long timeout, TimeUnit tu) throws InterruptedException {
        return logonWaiter.await(timeout, tu) && loggedIn;
    }

    private int nextRef() {
        return ref.getAndIncrement();
    }

    public static String buildRequest(String action, int ref, Map<String, String> params) {
        return "{\"id\": " + ref + ", \"action\": \"" + action + "\", \"params\": " + "{" +
                params.entrySet().stream()
                        .map(e -> encode(e.getKey()) + ": " + encode(e.getValue()))
                        .collect(Collectors.joining(",")) + "}}";
    }

    public static Map<String, String> parseJsonPlain(String json) {
        if (json == null) return null;
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return null;
        json = json.substring(1, json.length() - 1);

        Map<String, String> res = new HashMap<>();
        String[] keyValues = json.split(",");
        stream(keyValues).map(kv -> kv.split(":"))
                .filter(kv -> kv.length == 2)
                .forEach(kv -> res.put(decode(kv[0]), decode(kv[1])));

        return res;
    }

    public static String encode(String value) {
        return "\"" + value + "\"";
    }

    public static String decode(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static class Request {

        String type;
        int ref;
        Map<String, String> params = new HashMap<>();

        public Request(String type, int ref) {
            this.type = type;
            this.ref = ref;
        }

        Request addParam(String name, String value) {
            params.put(name, value);
            return this;
        }

        String toJson() {
            return buildRequest(type, ref, params);
        }
    }


    public static void main(String[] args) {
//        String resp = "{\"id\":2,\"type\":\"ok\",\"result\":\"ok\"}";
        String resp = "{\"id\":\"1\",\"type\":\"ok\",\"result\":{\"oper\":\"edit\",\"oid\":\"{46,96}\",\"fields\":{\"in1_value\":\"1.03506849472588484673e+01\",\"in2_value\":\"9.69080289187478882695e+00\"}}}";
        Map<String, String> map = parseJsonPlain(resp);

        map.entrySet().forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));
    }

    public static void main1(String[] args) throws URISyntaxException, InterruptedException, IOException {

        String login = "guest";
        String pass = "guest";

        String url = "ws://localhost:8000/websocket";


        EcometClient client = new EcometClient(new URI(url), login, pass);
        client.connect();

        if (client.waitForLogon(10, TimeUnit.SECONDS)) {
            System.out.println("Logged in");

            client.query("SUBSCRIBE CID=1 GET in1_value, in2_value WHERE AND(.folder='.PATH:/root/MODEL/DOM', .name='T.vozd.sred')");
        } else {
            System.out.println("Could not authorize!");
        }


//        {"id":"4","type":"ok","result":{"oper":"create","oid":"{46,96}","fields":{"in1_value":"1.03475709044123060920e+01","in2_value":"9.69691325704347839576e+00"}}}
//        {"id":4,"type":"ok","result":"ok"}


    }


}
