package upbit_candle.candle.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import upbit_candle.candle.Market.Market;

@Repository
public interface MarketRepository extends JpaRepository<Market, String> {

}