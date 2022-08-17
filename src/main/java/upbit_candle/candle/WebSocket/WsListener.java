package upbit_candle.candle.WebSocket;


import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import upbit_candle.candle.Entity.ConclusionEntity;
import upbit_candle.candle.Entity.Result.Conclusion;
import upbit_candle.candle.Entity.Result.OrderBookResult;
import upbit_candle.candle.Entity.Result.TradeResult;
import upbit_candle.candle.Service.ConclusionService;

/*
    ref.
    https://sas-study.tistory.com/432
 */

//@Component
@RequiredArgsConstructor
public final class WsListener extends WebSocketListener {
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private Gson gson = new Gson();
    private String json;
    private Conclusion conclusion;
    private ConclusionEntity cResult;
    private BigDecimal p;
    private List<String> codes;

    private ConclusionService service;

    public WsListener(ConclusionService service) {
        this.service = service;
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        System.out.printf("Socket Closed : %s / %s\r\n", code, reason);
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        System.out.printf("Socket Closing : %s / %s\n", code, reason);
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        webSocket.cancel();
    }

    @SneakyThrows
    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
        System.out.println("Socket Error : " + t.getMessage());
        webSocket.send(getParameter());
        Thread.sleep(2000);
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        JsonNode jsonNode = gson.fromJson(text, JsonNode.class);
        System.out.println(jsonNode.toPrettyString());
    }

    @SneakyThrows
    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        switch(conclusion) {
            case trade:
                TradeResult tradeResult = gson.fromJson(bytes.string(StandardCharsets.UTF_8), TradeResult.class);
                cResult = new ConclusionEntity(tradeResult.getCode(), tradeResult.getTrade_timestamp(), tradeResult.getTrade_price(), tradeResult.getTrade_volume(), tradeResult.getAsk_bid(), tradeResult.getTrade_date(), tradeResult.getTrade_time());
                ArrayList<WebSocketSession> list = OnMarketMap.map.computeIfAbsent(cResult.getCode(), k -> new ArrayList<>());
                if(list.size() != 0){
                    for (WebSocketSession ws : list) {
                        if(ws == null) continue;
                        if(BigDecimal.valueOf(OnMarketMap.pivotMap.getOrDefault(ws.getId(), 0L))
                                .compareTo(cResult.getReal_price()) > 0) continue;

                        ws.sendMessage(new TextMessage(gson.toJson(cResult)));
                    }
                }

                if(cResult.getReal_price().compareTo(p) < 0) break;
                service.save(cResult);
//                System.out.println(tradeResult);
                break;
            default:
                throw new RuntimeException("지원하지 않는 웹소켓 조회 유형입니다. : " + conclusion.getType());
        }

    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        webSocket.send(getParameter());
//        webSocket.close(NORMAL_CLOSURE_STATUS, null); // 없을 경우 끊임없이 서버와 통신함
    }

    public void setParameter(String UUID, Conclusion conclusion, List<String> codes, Long pivot) {
        this.conclusion = conclusion;
        this.codes = codes;
        this.json = gson.toJson(List.of(Ticket.of(UUID), Type.of(conclusion, codes)));
        this.p = new BigDecimal(pivot);
    }

    private String getParameter() {
        System.out.println(json);
        return this.json;
    }

    public ConclusionEntity getcResult(){
        return this.cResult;
    }

    @Data(staticConstructor = "of")
    private static class Ticket {
        private final String ticket;
    }

    @Data(staticConstructor = "of")
    private static class Type {
        private final Conclusion type;
        private final List<String> codes; // market
        private Boolean isOnlySnapshot = false;
        private Boolean isOnlyRealtime = true;
    }
}
