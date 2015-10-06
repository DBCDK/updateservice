//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'UpdateConstants' ];

//-----------------------------------------------------------------------------
var UpdateConstants = {

    //-----------------------------------------------------------------------------
    //                  Agency ids
    //-----------------------------------------------------------------------------

    COMMON_AGENCYID: "870970",

    RAWREPO_COMMON_AGENCYID: "191919",
    RAWREPO_DBC_ENRICHMENT_AGENCY_ID: "870970",

    DBC_LOGIN_AGENCY_ID: "010100",

    DBC_AGENCY_IDS: [ "010100" ],
    FBS_AGENCY_IDS: [
        "300101", "300147", "300151", "300153", "300155", "300157", "300159",
        "300161", "300163", "300165", "300167", "300169", "300173", "300175",
        "300183", "300185", "300187", "300190", "300201", "300210", "300217",
        "300219", "300223", "300230", "300240", "300250", "300253", "300259",
        "300260", "300265", "300269", "300270", "300306", "300316", "300320",
        "300326", "300329", "300330", "300336", "300340", "300350", "300360",
        "300370", "300376", "300390", "300400", "300410", "300411", "300420",
        "300430", "300440", "300450", "300461", "300479", "300480", "300482",
        "300492", "300510", "300530", "300540", "300550", "300561", "300563",
        "300573", "300575", "300580", "300607", "300615", "300621", "300630",
        "300657", "300661", "300665", "300671", "300706", "300707", "300710",
        "300727", "300730", "300740", "300741", "300746", "300751", "300756",
        "300760", "300766", "300773", "300779", "300787", "300791", "300810",
        "300813", "300820", "300825", "300840", "300846", "300849", "300851",
        "300860", "300970",
        "400700",
        "700400", "710100", "714700", "715100", "715300", "715500",
        "715700", "715900", "716100", "716300", "716500", "716700", "716900",
        "717300", "717500", "718300", "718500", "718700", "719000", "720100",
        "721000", "721700", "721900", "722300", "723000", "724000", "725000",
        "725300", "725900", "726000", "726500", "726900", "727000", "730600",
        "731600", "732000", "732600", "732900", "733000", "733600", "734000",
        "735000", "736000", "737000", "737600", "739000", "740000", "741000",
        "742000", "743000", "744000", "745000", "746100", "747900", "748000",
        "748200", "749200", "751000", "753000", "754000", "755000", "756100",
        "757300", "757500", "758000", "760700", "761500", "762100", "763000",
        "765700", "766100", "766500", "767100", "770600", "770700", "771000",
        "772700", "773000", "774000", "774100", "774600", "775100", "775600",
        "776000", "776600", "777300", "777900", "778700", "779100", "781000",
        "781300", "782000", "782500", "784000", "784600", "784900", "785100",
        "786000", "790900",
        "791615", "792615"
    ],

    //-----------------------------------------------------------------------------
    //                  Fields
    //-----------------------------------------------------------------------------

    DEFAULT_CLASSIFICATION_FIELDS: /004|008|009|038|039|100|110|239|245|652/,
    SINGLE_VOLUME_CLASSIFICATION_FIELDS: /004|009|038|039|100|110|239|245|652/,
    EXTENTABLE_NOTE_FIELDS: /504|530|531|600|610|631|666|770|780|795/,

    AUTH_ROOT_FEATURE: "auth_root"
};
