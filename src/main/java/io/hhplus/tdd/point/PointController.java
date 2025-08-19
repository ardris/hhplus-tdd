package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import io.hhplus.tdd.ErrorResponse;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    //0812 final 을 씀으로써 변함이 없게합시다. 
    private final UserPointTable utp;
    private final PointHistoryTable pht;


    //0812 어노테이션으로 의존성 주입이 가능 하지만 (@autoWired ) 그런거 잘몰라서.. 그냥 생성자 만듭시다.

    //  테스트 , 실무에서는 생성자 생성이 더 도움이 됨 이유는 다음과 같다.
    // 불변성 보장
    // final로 선언하면 한 번 주입된 의존성은 절대 바뀌지 않음.
    // 실수로 null 넣거나 다른 객체로 바꾸는 걸 막을 수 있음.₩
    // 테스트 편리함
    // 테스트 코드에서 직접 new PointController(mockUtp, mockPht) 식으로 주입 가능 → 스프링 안 띄워도 테스트 가능.
    // NPE 방지
    // 생성자에서 반드시 값을 넣으니, 주입이 안 되면 컴파일/런타임에서 바로 에러가 나서 문제를 빨리 발견.



    public PointController(UserPointTable utp , PointHistoryTable pht){
        this.utp = utp;
        this.pht = pht;
    }


    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point(
        //pathVariable 은 위의 id 처럼 중괄호 안에 있는 값을 변수처럼 쓸수 있게 해준다.
            @PathVariable long id
    ) {
        return utp.selectById(id);
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {

        return pht.selectAllByUserId(id);
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // 1. 충전 금액 유효성 체크 (0원 이하 불가)
        if (amount <= 0) {
            log.warn("충전 실패 - 금액이 0 이하임 : userId={}, amount={}", id, amount);
            throw new IllegalArgumentException("amount는 0보다 커야 합니다.");
        }
    
        // 2. 현재 포인트 조회
        UserPoint current = utp.selectById(id);
    
        // 3. long 범위 초과 방지 (계산 전에 체크해야 안전)
        // 이유 : newAmount 계산 후 체크하면 이미 오버플로우 발생 가능
        if (amount > Long.MAX_VALUE - current.point()) {
            log.error("충전 실패 - 최대 범위 초과 : userId={}, currentPoint={}, amount={}", id, current.point(), amount);
            throw new IllegalArgumentException("금액 합산이 ;ㅣ최대 범위를 초과합니다.");
        }
    
        // 4. 합산 후 포인트 업데이트
        long newAmount = current.point() + amount;
        UserPoint updated = utp.insertOrUpdate(id, newAmount);
    
        // 5. 충전 내역 기록 (updateMillis 사용)
        pht.insert(id, amount, TransactionType.CHARGE, updated.updateMillis());
    
        log.info("충전 성공 : userId={}, charged={}, newBalance={}", id, amount, updated.point());
        return updated;
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // 1. 사용 금액 유효성 체크
        if (amount <= 0) {
            log.warn("사용 실패 - 금액이 0 이하임 : userId={}, amount={}", id, amount);
            throw new IllegalArgumentException("amount는 0보다 커야 합니다.");
        }
    
        // 2. 현재 포인트 조회
        UserPoint current = utp.selectById(id);
    
        // 3. 잔액 부족 체크
        if (current.point() < amount) {
            log.warn("사용 실패 - 잔액 부족 : userId={}, balance={}, amount={}", id, current.point(), amount);
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }
    
        // 4. 쓰고 나ㅣㄴ 후 포인트 업데이트
        long newAmount = current.point() - amount;
        UserPoint updated = utp.insertOrUpdate(id, newAmount);
    
        // 5. 사용 내역 기록
        pht.insert(id, amount, TransactionType.USE, updated.updateMillis());
    
        log.info("사용 성공 : userId={}, used={}, newBalance={}", id, amount, updated.point());
        return updated;
    }
}
