# hubitat-weatherstation
Hubitat driver for weatherstack API

They allow 1K API calls per month so you can poll every hour with no problems. You will need your own api key from https://weatherstack.com/

Supports luminance which is calculated independently and can be updated more frequently.

Sets following state variables

cloud
clockSeconds
precip
sunsetTime
noonTime
localDate
twilight_end
tz_id
sunriseTime
twilight_begin
