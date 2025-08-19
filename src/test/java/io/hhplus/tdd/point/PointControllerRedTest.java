package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;



/**
 * 목적(RED):
 * - 현재 PointController는 엔티티(UserPoint/PointHistory)를 그대로 반환하고,
 *   예외는 IllegalArgumentException만.. 전역 구현 안함
 *
 *
 * 요약:
 *  1) 충전 실패(0원 이하) → 400 + {code:"BAD_REQUEST", message:"..."} 
 *  2) 사용 실패(잔액부족) → 409 + {code:"INSUFFICIENT_POINT", message:"..."} 
 *  3) 포인트 조회 → {userId,balance} DTO로 응답(엔티티 필드 노출 금지)
 *  4) 내역 조회 → [{type,amount,timestamp}] DTO 배열로 응답(엔티티 필드 노출 금지)
 *
 */
@WebMvcTest(controllers = io.hhplus.tdd.point.PointController.class) // FQCN로 못박기
class PointControllerRedTest {

    //실제 톰캣/네티 같은 서버를 띄우지 않고 스프링 MVC 디스패처(서블릿)만 올려서 HTTP 요청/응답을 시뮬레이션하는 테스트 클라이언트.
    //mockmvc 랑 mockbean  순수  / 스프링 빈 
    @Autowired MockMvc mvc;

    @MockBean UserPointTable utp;
    @MockBean PointHistoryTable pht;




    // 1) PATCH /point/{id}/charge : 유효성 위반(0 이하면) 
    // 테스트 역할 : 충전 금액 0 이하 일 시 
    @Test
    
    @DisplayName("PATCH /point/{id}/charge )")
    void charge_invalidAmount_red_e() throws Exception {
        long userId = 1L;

        mvc.perform(patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("0"))
                .andDo(print()) 
           .andExpect(status().isBadRequest())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
           .andExpect(jsonPath("$.message").exists());
    }

    // 2) PATCH /point/{id}/use : 잔액 부족 
    // 테스트 역할 : 충전 금액 사용 시 잔액이 부족 할 때
    @Test
    @DisplayName("PATCH /point/{id}/use ")
    void use_insufficient_red_error() throws Exception {
        long userId = 1L;
        // 현재 잔액 100 세팅
        given(utp.selectById(userId)).willReturn(new UserPoint(userId, 10000L, System.currentTimeMillis()));

        mvc.perform(patch("/point/{id}/use", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("200"))
                .andDo(print()) 

           .andExpect(status().isConflict()) // 409 로 강제 지정
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"))
           .andExpect(jsonPath("$.message").exists());
    }
        //성공 예시
        @Test
        @DisplayName("PATCH /point/{id}/use - 정상 사용 시 200")
        void use_success_returns_ok() throws Exception {
            long userId = 1L;
            long now = System.currentTimeMillis();

            // 1) 준비: 잔액 10_000
            given(utp.selectById(userId))
                .willReturn(new UserPoint(userId, 10_000L, now));

            // 2) 5_400 사용 → 새 잔액 4_600
            given(utp.insertOrUpdate(userId, 4_600L))
                .willReturn(new UserPoint(userId, 4_600L, now + 1));

            mvc.perform(patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("5400"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.point").value(4_600)) 
            .andExpect(jsonPath("$.updateMillis").value((int)(now + 1)));

            // 부가 검증 성공하면 로그에 안남네여
            verify(utp).insertOrUpdate(userId, 4_600L);
            verify(pht).insert(eq(userId), eq(5_400L), eq(TransactionType.USE), eq(now + 1));
        }



    // 3) GET /point/{id} :
    // 테스트 역할 : 아이디 로 포인트 조회 
    // *  제공 메소드 내 크게 에러를 잡을 게 없어서 낭비 테스트이지 않을까 함. 
    @Test
    @DisplayName("GET /point/{id} ")
    void get_point_red_error() throws Exception {
        long userId = 1L;
        given(utp.selectById(userId)).willReturn(new UserPoint(userId, 1300L, 111111111));

        mvc.perform(get("/point/{id}", userId))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andDo(print()) 

           .andExpect(jsonPath("$.userId").value(1))
           .andExpect(jsonPath("$.balance").value(1500))
           .andExpect(jsonPath("$.id").doesNotExist())
           .andExpect(jsonPath("$.point").doesNotExist())
           .andExpect(jsonPath("$.updateMillis").doesNotExist());
    }

    // 4) GET /point/{id}/histories : 이력 조회
    @Test
    @DisplayName("GET /point/{id}/histories ")
    void get_histories_red_error() throws Exception {
        long userId = 1L;
        given(pht.selectAllByUserId(userId)).willReturn(List.of(
                new PointHistory(1,userId, 200L, TransactionType.USE, 2000L),
                new PointHistory(1, userId, 500L, TransactionType.CHARGE, 1000L)
        ));

        mvc.perform(get("/point/{id}/histories", userId))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andDo(print()) 

           .andExpect(jsonPath("$[0].type").value("USE"))
           .andExpect(jsonPath("$[0].amount").value(200))
           .andExpect(jsonPath("$[0].timestamp").value(2000))
           .andExpect(jsonPath("$[0].userId").doesNotExist())
           .andExpect(jsonPath("$[0].updateMillis").doesNotExist());
    }
}
