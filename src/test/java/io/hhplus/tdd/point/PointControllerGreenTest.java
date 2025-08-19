package io.hhplus.tdd.point;

import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = io.hhplus.tdd.point.PointController.class)
class PointControllerGreenTest {

    @Autowired MockMvc mvc;

    @MockBean UserPointTable utp;
    @MockBean PointHistoryTable pht;

    @Test
    @DisplayName("포인트 충전 성공")
    void charge_success() throws Exception {
        long userId = 1L;
        long now = System.currentTimeMillis();

        given(utp.selectById(userId))
                .willReturn(new UserPoint(userId, 100L, now));
        given(utp.insertOrUpdate(eq(userId), eq(300L)))
                .willReturn(new UserPoint(userId, 300L, now + 1));//ㅎㅎ;

        mvc.perform(patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("200"))
           .andDo(print())
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(1))
           .andExpect(jsonPath("$.point").value(300))
           .andExpect(jsonPath("$.updateMillis").exists());

        verify(utp).insertOrUpdate(userId, 300L);
        verify(pht).insert(eq(userId), eq(200L), eq(TransactionType.CHARGE), eq(now + 1));
    }

    @Test
    @DisplayName("포인트 사용 성공")
    void use_success() throws Exception {
        long userId = 1L;
        long now = System.currentTimeMillis();

        given(utp.selectById(userId)) 
                .willReturn(new UserPoint(userId, 500L, now));
        given(utp.insertOrUpdate(eq(userId), eq(300L)))
                .willReturn(new UserPoint(userId, 300L, now + 1));

        mvc.perform(patch("/point/{id}/use", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("200"))
           .andDo(print())
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(1))
           .andExpect(jsonPath("$.point").value(300))
           .andExpect(jsonPath("$.updateMillis").exists());

        verify(utp).insertOrUpdate(userId, 300L);
        verify(pht).insert(eq(userId), eq(200L), eq(TransactionType.USE), eq(now + 1));
    }

    @Test
    @DisplayName("포인트 조회 성공")
    void get_point_success() throws Exception {    
        long userId = 1L;
        long ts = 123456789L;

        given(utp.selectById(userId))
                .willReturn(new UserPoint(userId, 1500L, ts));

        mvc.perform(get("/point/{id}", userId))
           .andDo(print())
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(1))
           .andExpect(jsonPath("$.point").value(1500))
           .andExpect(jsonPath("$.updateMillis").value((int) ts));
    }

    @Test
    @DisplayName("포인트 내역 조회 성공")
    void get_histories_success() throws Exception {
        long userId = 1L;


        given(pht.selectAllByUserId(userId))
    .willReturn(List.of(
        new PointHistory(1L, userId, 200L, TransactionType.USE, 2000L),
        new PointHistory(2L, userId, 500L, TransactionType.CHARGE, 1000L)
    ));

        mvc.perform(get("/point/{id}/histories", userId))
           .andDo(print())
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].amount").value(200))
           .andExpect(jsonPath("$[0].type").value("USE"))
           .andExpect(jsonPath("$[0].updateMillis").value(2000))
           .andExpect(jsonPath("$[1].amount").value(500))
           .andExpect(jsonPath("$[1].type").value("CHARGE"))
           .andExpect(jsonPath("$[1].updateMillis").value(1000));
    }
}
