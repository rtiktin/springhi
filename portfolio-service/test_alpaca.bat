@echo off
curl --request GET --url "https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,TSLA" --header "APCA-API-KEY-ID: PKQMUV2D4SCX5GNLAPYUEPXT7S" --header "APCA-API-SECRET-KEY: uJBrkG98LZt1pobApxAwae8Yb4ZCx6GjKcAnJwF8EPP" --header "accept: application/json" --insecure
