package tom.burrows.commons.dtos;

import java.util.ArrayList;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class Coin {
    
    String id;
    String symbol;
    String name;
    String image;
    @JsonAlias("current_price")
    Long currentPrice;
    @JsonAlias("market_cap")
    Long marketCap;
    @JsonAlias("market_cap_rank")
    Integer marketCapRank;
    @JsonAlias("market_cap_rank_with_rehypothecated")
    Integer marketCapRankWithRehypocated;
    @JsonAlias("market_cap_change_24h")
    Double marketCapChange24h;
    @JsonAlias("market_cap_change_percentage_24h")    
    Double marketCapChangePercent24h; 
    @JsonAlias("fully_diluted_valuation")
    Long fullyDilutedVal;
    @JsonAlias("total_volume")
    Long totalVolume;
    @JsonAlias("high_24h")
    Long high24h;
    @JsonAlias("low_24h")
    Long low24h;
    @JsonAlias("price_change_24h")
    Long priceChange24h;
    @JsonAlias("price_change_percentage_24h")
    Double priceChangePercent24h;
    @JsonAlias("circulating_supply")
    Long circulatingSupply;
    @JsonAlias("total_supply")
    Long totalSupply;
    @JsonAlias("max_supply")
    Long maxSupply;
    Long ath;
    @JsonAlias("ath_change_percentage")
    Long athChangePercent;
    @JsonAlias("ath_date")
    Date athDate;
    Long atl;
    @JsonAlias("atl_change_percentage")
    Long atlChangePercent;
    @JsonAlias("atl_date")
    Date atlDate;
    Long roi;
    @JsonAlias("sparkline_in_7d")
    ArrayList<Double> sparkLine7d;
    @JsonAlias("price_change_percentage_1h_in_currency")
    Double priceChangePercentageInCurrency1h;
    @JsonAlias("price_change_percentage_24h_in_currency")
    Double priceChangePercentageInCurrency24h;
    @JsonAlias("price_change_percentage_7d_in_currency")
    Double priceChangePercentageInCurrency7d;
    @JsonAlias("price_change_percentage_14d_in_currency")
    Double priceChangePercentageInCurrency14d;
    @JsonAlias("price_change_percentage_30d_in_currency")
    Double priceChangePercentageInCurrency30d;
    @JsonAlias("price_change_percentage_200d_in_currency")
    Double priceChangePercentageInCurrency200d;
    @JsonAlias("price_change_percentage_1y_in_currency")
    Double priceChangePercentageInCurrency1y;
    
    @JsonAlias("last_updated")
    Date lastUpdated;

}