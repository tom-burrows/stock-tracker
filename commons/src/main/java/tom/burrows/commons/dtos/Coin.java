package tom.burrows.commons.dtos;

import java.util.ArrayList;
import java.util.Date;

public class Coin {

    String id;
    String symbol;
    String name;
    String image;
    Long currentPrice;
    Long marketCap;
    Integer marketCapRank;
    Integer marketCapRankWithRehypocated;
    Double marketCapChange24h;
    Double marketCapChangePercent24h; 
    Long fullyDilutedVal;
    Long totalVolume;
    Long high24h;
    Long low24h;
    Long priceChange24h;
    Double priceChangePercent24h;
    Long circulatingSupply;
    Long totalSupply;
    Long maxSupply;
    Long ath;
    Long athChangePercent;
    Date athDate;
    Long atl;
    Long atlChangePercent;
    Date atlDate;
    Long roi;
    ArrayList<Double> sparkLine7d;
    Double priceChangePercentageInCurrency1h;
    Double priceChangePercentageInCurrency24h;
    Double priceChangePercentageInCurrency7d;
    Double priceChangePercentageInCurrency14d;
    Double priceChangePercentageInCurrency30d;
    Double priceChangePercentageInCurrency200d;
    Double priceChangePercentageInCurrency1y;
    

    Date lastUpdated;
    
}