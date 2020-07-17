/***********************************************************************************************************************
*  Copyright 2020 craigde
*
*  Contributors:
*       https://github.com/adey/bangali/blob/master/driver/apixu-weather.groovy    old apiux driver used as a starting point 
*       https://github.com/jebbett      code for new weather icons based on weather condition data
*       https://www.deviantart.com/vclouds/art/VClouds-Weather-Icons-179152045     new weather icons courtesy of VClouds
*		https://github.com/arnbme		code for mytile
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*  Weatherstack Weather Driver
*
*  Author: craigde
*
*  Date: 2020-07-17
*
*  attribution: weather data courtesy: https://api.weatherstack.com/ - see https://weatherstack.com/documentation
*
*  attribution: sunrise and sunset courtesy: https://sunrise-sunset.org/
*
* for use with HUBITAT
*
* features:
* - supports global weather data with free api key from https://weatherstack.com/
* - provides calculated illuminance data based on time of day and weather condition code.
* - no local server setup needed
* - no personal weather station needed
*
***********************************************************************************************************************/

public static String version()      {  return "v1.03"  }

/***********************************************************************************************************************
*
* Version 1
*   5/31/2020: 1.0 - version 1 for weatherstack api
*   6/1/2020:  1.01 - added hourly refresh rate
*   7/12/2020: 1.02 - minor bug fixes
*   7/17/2020: 1.03 - Added option to use automatic hub location and fixed bug where manual city names with spaces in them failed
*/

import groovy.transform.Field

metadata    {
    definition (name: "Weatherstack Weather Driver", namespace: "craigde", author: "craigde")  {
        capability "Actuator"
        capability "Sensor"
        capability "Polling"
        capability "Illuminance Measurement"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Pressure Measurement"
        capability "Ultraviolet Index"

        attribute "name", "string"
        attribute "region", "string"
        attribute "country", "string"
        attribute "lat", "string"
        attribute "lon", "string"
        attribute "tz_id", "string"
        attribute "localtime_epoch", "string"
        attribute "local_time", "string"
        attribute "local_date", "string"
        attribute "last_updated_epoch", "string"
        attribute "last_updated", "string"
        attribute "is_day", "string"
        attribute "condition_text", "string"
        attribute "condition_icon", "string"
        attribute "condition_icon_url", "string"
        attribute "condition_code", "string"
        attribute "visual", "string"
        attribute "visualWithText", "string"
        attribute "wind_mph", "string"
        attribute "wind_kph", "string"
		attribute "wind_mps", "string"
        attribute "wind_degree", "string"
        attribute "wind_dir", "string"

        attribute "cloud", "string"
        attribute "feelsLike_c", "string"
        attribute "feelsLike_f", "string"
        attribute "vis_km", "string"
        attribute "vis_miles", "string"

        attribute "location", "string"
        attribute "city", "string"
        attribute "local_sunrise", "string"
        attribute "local_sunset", "string"
        attribute "twilight_begin", "string"
        attribute "twilight_end", "string"
        attribute "illuminated", "string"
        attribute "cCF", "string"
        attribute "lastXUupdate", "string"

        attribute "weather", "string"
        attribute "feelsLike", "string"
        attribute "wind", "string"
        attribute "precip", "string"
   
        attribute "localSunrise", "string"
        attribute "localSunset", "string"

        attribute "wind_mytile", "string"
        attribute "mytile", "string"
        
        command "refresh"
    }

    preferences     {
		input "useHubLocation", "bool", title:"Use Automatic Hub Location", required:true, defaultValue:true
        input "hubLocation", "text", title:"Enter Zip code, city name or latitude,longitude if not using Automation Hub Location", required:false
        input "apiKey", "text", title:"WeatherStack key?", required:true
        input "cityName", "text", title: "Override default city name?", required:false, defaultValue:null
        input "isFahrenheit", "bool", title:"Use Imperial units?", required:true, defaultValue:true
        input "dashClock", "bool", title:"Udate dashboard clock':' every 2 seconds?", required:true, defaultValue:false
        input "pollEvery", "enum", title:"Poll Api interval?\nrecommended setting 60 minutes.\nilluminance is updated independently.", required:true, defaultValue:"1Hour", options:["15Minutes":"15 minutes","30Minutes":"30 minutes","1Hour":"60 minutes", "2Hour":"120 minutes"]
		input "luxEvery", "enum", title:"Illuminance update interval?", required:true, defaultValue:"5Minutes", options:["5Minutes":"5 minutes","10Minutes":"10 minutes","15Minutes":"15 minutes","30Minutes":"30 minutes","1Hour":"60 minutes"]
		input "isDebug", "bool", title:"Debug mode", required:true, defaultValue:false
   }

}

def updated()   {
	unschedule()
    state.tz_id = null
	state.localDate = null
    state.clockSeconds = true
    state.precip = null
    poll()
    if (isDebug) {log.debug ">>>>> api polltime: $pollEvery"}

    "runEvery${pollEvery}"(poll)
    "runEvery${luxEvery}"(updateLux)
    
    if (dashClock)  updateClock();
}

def poll()      {
    if (isDebug) {log.debug ">>>>> api: Executing 'poll', location: $location"}

    def obs = [:]
    
    if (isFahrenheit) {
       
       obs = getXUdata("F")
    }
   else  {
       
       obs = getXUdata("M")
    }

   if (isDebug) {log.debug ">>>>> api: returned - $obs"}
    
   if (obs==[:])   {
       log.warn "No response from WeatherStack API"
       return
   }
    

    def now = new Date().format('yyyy-MM-dd HH:mm', location.timeZone)
    sendEvent(name: "lastXUupdate", value: now, displayed: true)

    def tZ = TimeZone.getTimeZone(obs.location.timezone_id)
    state.tz_id = obs.location.timezone_id

    def localTime = new Date().parse("yyyy-MM-dd HH:mm", obs.location.localtime, tZ)
    def localDate = localTime.format("yyyy-MM-dd", tZ)
    def localTimeOnly = localTime.format("HH:mm", tZ)

    def sunriseAndSunset = getSunriseAndSunset(obs.location.lat, obs.location.lon, localDate)
    def sunriseTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.sunrise, tZ)
    def sunsetTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.sunset, tZ)
    def noonTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.solar_noon, tZ)
    def twilight_begin = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.civil_twilight_begin, tZ)
    def twilight_end = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.civil_twilight_end, tZ)

    def localSunrise = sunriseTime.format("HH:mm", tZ)
    sendEventPublish(name: "local_sunrise", value: localSunrise, descriptionText: "Sunrise today is at $localSunrise", displayed: true)
    def localSunset = sunsetTime.format("HH:mm", tZ)
    sendEventPublish(name: "local_sunset", value: localSunset, descriptionText: "Sunset today at is $localSunset", displayed: true)
    def tB = twilight_begin.format("HH:mm", tZ)
    sendEventPublish(name: "twilight_begin", value: tB, descriptionText: "Twilight begins today at $tB", displayed: true)
    def tE = twilight_end.format("HH:mm", tZ)
    sendEventPublish(name: "twilight_end", value: tE, descriptionText: "Twilight ends today at $tE", displayed: true)

    state.sunriseTime = sunriseTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.sunsetTime = sunsetTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.noonTime = noonTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.twilight_begin = twilight_begin.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.twilight_end = twilight_end.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)

    sendEventPublish(name: "name", value: obs.location.name, displayed: true)
    sendEventPublish(name: "region", value: obs.location.region, displayed: true)
    sendEventPublish(name: "country", value: obs.location.country, displayed: true)
    sendEventPublish(name: "lat", value: obs.location.lat, displayed: true)
    sendEventPublish(name: "lon", value: obs.location.lon, displayed: true)
    sendEventPublish(name: "tz_id", value: obs.location.timezone_id, displayed: true)
    sendEventPublish(name: "localtime_epoch", value: obs.location.localtime_epoch, displayed: true)
    sendEventPublish(name: "local_time", value: localTimeOnly, displayed: true)
    sendEventPublish(name: "local_date", value: localDate, displayed: true)

    sendEventPublish(name: "temperature", value: obs.current.temperature, unit: "${(isFahrenheit ? 'F' : 'C')}", displayed: true)
    sendEventPublish(name: "is_day", value: obs.current.is_day, displayed: true)
    sendEventPublish(name: "condition_text", value: obs.current.weather_descriptions, displayed: true)
    sendEventPublish(name: "condition_icon", value: '<img src=' + obs.current.weather_icons + '>', displayed: true)
    sendEventPublish(name: "condition_icon_url", value: obs.current.weather_icons, displayed: true)
    sendEventPublish(name: "condition_code", value: obs.current.weather_code, displayed: true)
    def imgName = getImgName(obs.current.weather_code, obs.current.is_day)
    sendEventPublish(name: "visual", value: '<img src=' + imgName + '>', displayed: true)
    sendEventPublish(name: "visualWithText", value: '<img src=' + imgName + '><br>' + obs.current.weather_descriptions, displayed: true)
	if (isFahrenheit)	{
	    sendEventPublish(name: "wind_mph", value: obs.current.wind_speed, unit: "MPH", displayed: true)
		sendEventPublish(name: "wind_mps", value: ((obs.current.wind_speed / 3.6f).round(1)), unit: "MPS", displayed: true)
	}
	else
    	sendEventPublish(name: "wind_kph", value: obs.current.wind_speed, unit: "KPH", displayed: true)
    sendEventPublish(name: "wind_degree", value: obs.current.wind_degree, unit: "DEGREE", displayed: true)
    sendEventPublish(name: "wind_dir", value: obs.current.wind_dir, displayed: true)
    sendEventPublish(name: "pressure", value: obs.current.pressure, unit: "${(isFahrenheit ? 'IN' : 'MBAR')}", displayed: true)
    sendEventPublish(name: "precip", value: obs.current.precip, unit: "${(isFahrenheit ? 'IN' : 'MM')}", displayed: true)
    sendEventPublish(name: "humidity", value: obs.current.humidity, unit: "%", displayed: true)
    sendEventPublish(name: "cloud", value: obs.current.cloudcover, unit: "%", displayed: true)

    sendEventPublish(name: "location", value: obs.location.name + ', ' + obs.location.region, displayed: true)
    state.condition_code = obs.current.weather_code
    state.cloud = obs.current.cloudcover
    state.precip = obs.current.precip
    updateLux()

    sendEventPublish(name: "city", value: (cityName ?: obs.location.name), displayed: true)
    sendEventPublish(name: "weather", value: obs.current.weather_descriptions, displayed: true)
    sendEventPublish(name: "feelsLike", value: obs.current.feelslike, unit: "${(isFahrenheit ? 'F' : 'C')}", displayed: true)
    sendEventPublish(name: "wind", value: obs.current.wind_speed, unit: "${(isFahrenheit ? 'MPH' : 'KPH')}", displayed: true)
    sendEventPublish(name: "localSunrise", value: localSunrise, displayed: true)
    sendEventPublish(name: "localSunset", value: localSunset, displayed: true)

	def wind_mytile=(isFahrenheit ? "${Math.round(obs.current.wind_speed)}" + " mph " : "${Math.round(obs.current.wind_speed)}" + " kph ")
	sendEventPublish(name: "wind_mytile", value: wind_mytile, displayed: true)

    def mytext = (cityName ?: obs.location.name) + ', ' + obs.location.region 
   	
    mytext += '<br>' + (isFahrenheit ? "${Math.round(obs.current.temperature)}" + '&deg;F ' : obs.current.temperature + '&deg;C ') + obs.current.humidity + '%'
	mytext += '<br>' + localSunrise + ' <img style="height:2em" src=' + imgName + '> ' + localSunset
	mytext += (wind_mytile == (isFahrenheit ? "0 mph " : "0 kph ") ? '<br> Wind is calm' : '<br>' + obs.current.wind_dir + ' ' + wind_mytile)
	mytext += '<br>' + obs.current.weather_descriptions

    sendEventPublish(name: "mytile", value: mytext, displayed: true)
    return
}

def refresh()       { poll() }

def configure()     { poll() }

def getXUdata(units)   {
    def obs = [:]
    def params = [:]
    
    if (useHubLocation) {
       params = [ uri: "http://api.weatherstack.com/current?access_key=$apiKey&query=$location.latitude,$location.longitude&units=$units" ]
    }
    else {
       hubLocation = replaceAll(hubLocation)
        params = [ uri: "http://api.weatherstack.com/current?access_key=$apiKey&query=$hubLocation&units=$units" ]
    }
    
    if (isDebug) { log.debug "$params" }
    
    try {
        httpGet(params)		{ resp ->
            if (resp?.data)     obs << resp.data;
            else                log.error "http call for Weatherstack weather api did not return data: $resp";
        }
    } catch (e) { log.error "http call failed for Weatherstack weather api: $e" }
    if (isDebug) { log.debug "$obs" }
    return obs
}

private getSunriseAndSunset(latitude, longitude, forDate)	{
    def params = [ uri: "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=$forDate&formatted=0" ]
    def sunRiseAndSet = [:]
    try {
        httpGet(params)		{ resp -> sunRiseAndSet = resp.data }
    } catch (e) { log.error "http call failed for sunrise and sunset api: $e" }

    return sunRiseAndSet
}

def updateLux()     {
    if (!state.sunriseTime || !state.sunsetTime || !state.noonTime || !state.twilight_begin || !state.twilight_end || !state.tz_id)
        return

    def tZ = TimeZone.getTimeZone(state.tz_id)
    def lT = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    def localTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", lT, tZ)
    def sunriseTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunriseTime, tZ)
    def sunsetTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunsetTime, tZ)
    def noonTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.noonTime, tZ)
    def twilight_begin = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.twilight_begin, tZ)
    def twilight_end = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.twilight_end, tZ)
    def lux = estimateLux(localTime, sunriseTime, sunsetTime, noonTime, twilight_begin, twilight_end, state.condition_code, state.cloud, state.tz_id)
    sendEventPublish(name: "illuminance", value: lux, unit: "lux", displayed: true)
    sendEventPublish(name: "illuminated", value: String.format("%,d lux", lux), displayed: true)
}

private estimateLux(localTime, sunriseTime, sunsetTime, noonTime, twilight_begin, twilight_end, condition_code, cloud, tz_id)     {
    if (isDebug) {
       log.debug "condition_code: $condition_code | cloud: $cloud"
       log.debug "twilight_begin: $twilight_begin | twilight_end: $twilight_end | tz_id: $tz_id"
       log.debug "localTime: $localTime | sunriseTime: $sunriseTime | noonTime: $noonTime | sunsetTime: $sunsetTime"
    }
    
    def tZ = TimeZone.getTimeZone(tz_id)
    def lux = 0l
    def aFCC = true
    def l

    if (timeOfDayIsBetween(sunriseTime, noonTime, localTime, tZ))      {
        if (isDebug) { log.debug "between sunrise and noon" }
        l = (((localTime.getTime() - sunriseTime.getTime()) * 10000f) / (noonTime.getTime() - sunriseTime.getTime()))
        lux = (l < 50f ? 50l : l.trunc(0) as long)
    }
    else if (timeOfDayIsBetween(noonTime, sunsetTime, localTime, tZ))      {
    if (isDebug) { log.debug "between noon and sunset" }
        l = (((sunsetTime.getTime() - localTime.getTime()) * 10000f) / (sunsetTime.getTime() - noonTime.getTime()))
        lux = (l < 50f ? 50l : l.trunc(0) as long)
    }
    else if (timeOfDayIsBetween(twilight_begin, sunriseTime, localTime, tZ))      {
        if (isDebug) { log.debug "between sunrise and twilight" }
        l = (((localTime.getTime() - twilight_begin.getTime()) * 50f) / (sunriseTime.getTime() - twilight_begin.getTime()))
        lux = (l < 10f ? 10l : l.trunc(0) as long)
    }
    else if (timeOfDayIsBetween(sunsetTime, twilight_end, localTime, tZ))      {
        if (isDebug) { log.debug "between sunset and twilight" }
        l = (((twilight_end.getTime() - localTime.getTime()) * 50f) / (twilight_end.getTime() - sunsetTime.getTime()))
        lux = (l < 10f ? 10l : l.trunc(0) as long)
    }
    else if (!timeOfDayIsBetween(twilight_begin, twilight_end, localTime, tZ))      {
        if (isDebug) { log.debug "between non-twilight" }
        lux = 5l
        aFCC = false
    }

    def cC = condition_code.toInteger()
    def cCT = ''
    def cCF
    if (aFCC)
        if (conditionFactor[cC])    {
            cCF = conditionFactor[cC][1]
            cCT = conditionFactor[cC][0]
        }
        else    {
            cCF = ((100 - (cloud.toInteger() / 3d)) / 100).round(1)
            cCT = 'using cloud cover'
        }
    else    {
        cCF = 1.0
        cCT = 'night time now'
    }

    lux = (lux * cCF) as long
    if (isDebug) { log.debug "condition: $cC | condition text: $cCT | condition factor: $cCF | lux: $lux" }
    sendEventPublish(name: "cCF", value: cCF, displayed: true)

    return lux
}

private timeOfDayIsBetween(fromDate, toDate, checkDate, timeZone)     {
    return (!checkDate.before(fromDate) && !checkDate.after(toDate))
}

private sendEventPublish(evt)	{
	def var = "${evt.name + 'Publish'}"
    if (isDebug) { log.debug var }
	def pub = this[var]
	if (pub)		sendEvent(name: evt.name, value: evt.value, descriptionText: evt.descriptionText, unit: evt.unit, displayed: evt.displayed);
    if (isDebug) { log.debug pub }
}

def updateClock()       {
    runIn(2, updateClock)
    if (!state.tz_id)       return;
    if (!tz_id)       return;
    def nowTime = new Date()
    def tZ = TimeZone.getTimeZone(state.tz_id)
    sendEventPublish(name: "local_time", value: nowTime.format((state.clockSeconds ? "HH:mm" : "HH mm"), tZ), displayed: true)
    def localDate = nowTime.format("yyyy-MM-dd", tZ)
    if (localDate != state.localDate)
    {   state.localDate = localDate
        sendEventPublish(name: "local_date", value: localDate, displayed: true)
    }
    state.clockSeconds = (state.clockSeconds ? false : true)
}


@Field final Map    conditionFactor = [
        113: ['Sunny', 1, 'sunny'],                                        116: ['Partly cloudy', 0.8, 'partlycloudy'],
        119: ['Cloudy', 0.6, 'cloudy'],                                    122: ['Overcast', 0.5, 'cloudy'],
        143: ['Mist', 0.5, 'fog'],                                         176: ['Patchy rain possible', 0.8, 'chancerain'],
        179: ['Patchy snow possible', 0.6, 'chancesnow'],                  182: ['Patchy sleet possible', 0.6, 'chancesleet'],
        185: ['Patchy freezing drizzle possible', 0.4, 'chancesleet'],     200: ['Thundery outbreaks possible', 0.2, 'chancetstorms'],
        227: ['Blowing snow', 0.3, 'snow'],                                230: ['Blizzard', 0.1, 'snow'],
        248: ['Fog', 0.2, 'fog'],                                          260: ['Freezing fog', 0.1, 'fog'],
        263: ['Patchy light drizzle', 0.8, 'rain'],                        266: ['Light drizzle', 0.7, 'rain'],
        281: ['Freezing drizzle', 0.5, 'sleet'],                           284: ['Heavy freezing drizzle', 0.2, 'sleet'],
        293: ['Patchy light rain', 0.8, 'rain'],                           296: ['Light rain', 0.7, 'rain'],
        299: ['Moderate rain at times', 0.5, 'rain'],                      302: ['Moderate rain', 0.4, 'rain'],
        305: ['Heavy rain at times', 0.3, 'rain'],                         308: ['Heavy rain', 0.2, 'rain'],
        311: ['Light freezing rain', 0.7, 'sleet'],                        314: ['Moderate or heavy freezing rain', 0.3, 'sleet'],
        317: ['Light sleet', 0.5, 'sleet'],                                320: ['Moderate or heavy sleet', 0.3, 'sleet'],
        323: ['Patchy light snow', 0.8, 'flurries'],                       326: ['Light snow', 0.7, 'snow'],
        329: ['Patchy moderate snow', 0.6, 'snow'],                        332: ['Moderate snow', 0.5, 'snow'],
        335: ['Patchy heavy snow', 0.4, 'snow'],                           338: ['Heavy snow', 0.3, 'snow'],
        350: ['Ice pellets', 0.5, 'sleet'],                                353: ['Light rain shower', 0.8, 'rain'],
        356: ['Moderate or heavy rain shower', 0.3, 'rain'],               359: ['Torrential rain shower', 0.1, 'rain'],
        362: ['Light sleet showers', 0.7, 'sleet'],                        365: ['Moderate or heavy sleet showers', 0.5, 'sleet'],
        368: ['Light snow showers', 0.7, 'snow'],                          371: ['Moderate or heavy snow showers', 0.5, 'snow'],
        374: ['Light showers of ice pellets', 0.7, 'sleet'],               377: ['Moderate or heavy showers of ice pellets',0.3, 'sleet'],
        386: ['Patchy light rain with thunder', 0.5, 'tstorms'],           389: ['Moderate or heavy rain with thunder', 0.3, 'tstorms'],
        392: ['Patchy light snow with thunder', 0.5, 'tstorms'],           395: ['Moderate or heavy snow with thunder', 0.3, 'tstorms']
    ]

private getImgName(wCode, is_day)       {
    def url = "https://cdn.rawgit.com/adey/bangali/master/resources/icons/weather/"
    def imgItem = imgNames.find{ it.code == wCode && it.day == is_day }

    if (isDebug) {
        log.debug "wCode is $wCode"
        log.debug "is_day is $is_day"
        log.debug "imgItem is $imgItem"}
    return (url + (imgItem ? imgItem.img : 'na.png'))
}

public static String replaceAll(String str) {
        String[] words = str.split(" ");
        StringBuilder sentence = new StringBuilder(words[0]);
 
        for (int i = 1; i < words.length; ++i) {
            sentence.append("%20");
            sentence.append(words[i]);
        }
 
        return sentence.toString();
    }

@Field final List    imgNames =     [
        [code: 113, day: "yes", img: '32.png', ],	// DAY - Sunny
        [code: 116, day: "yes", img: '30.png', ],	// DAY - Partly cloudy
        [code: 119, day: "yes", img: '28.png', ],	// DAY - Cloudy
        [code: 122, day: "yes", img: '26.png', ],	// DAY - Overcast
        [code: 143, day: "yes", img: '20.png', ],	// DAY - Mist
        [code: 176, day: "yes", img: '39.png', ],	// DAY - Patchy rain possible
        [code: 179, day: "yes", img: '41.png', ],	// DAY - Patchy snow possible
        [code: 182, day: "yes", img: '41.png', ],	// DAY - Patchy sleet possible
        [code: 185, day: "yes", img: '39.png', ],	// DAY - Patchy freezing drizzle possible
        [code: 200, day: "yes", img: '38.png', ],	// DAY - Thundery outbreaks possible
        [code: 227, day: "yes", img: '15.png', ],	// DAY - Blowing snow
        [code: 230, day: "yes", img: '16.png', ],	// DAY - Blizzard
        [code: 248, day: "yes", img: '21.png', ],	// DAY - Fog
        [code: 260, day: "yes", img: '21.png', ],	// DAY - Freezing fog
        [code: 263, day: "yes", img: '39.png', ],	// DAY - Patchy light drizzle
        [code: 266, day: "yes", img: '11.png', ],	// DAY - Light drizzle
        [code: 281, day: "yes", img: '8.png', ],	// DAY - Freezing drizzle
        [code: 284, day: "yes", img: '10.png', ],	// DAY - Heavy freezing drizzle
        [code: 293, day: "yes", img: '39.png', ],	// DAY - Patchy light rain
        [code: 296, day: "yes", img: '11.png', ],	// DAY - Light rain
        [code: 299, day: "yes", img: '39.png', ],	// DAY - Moderate rain at times
        [code: 302, day: "yes", img: '12.png', ],	// DAY - Moderate rain
        [code: 305, day: "yes", img: '39.png', ],	// DAY - Heavy rain at times
        [code: 308, day: "yes", img: '12.png', ],	// DAY - Heavy rain
        [code: 311, day: "yes", img: '8.png', ],	// DAY - Light freezing rain
        [code: 314, day: "yes", img: '10.png', ],	// DAY - Moderate or heavy freezing rain
        [code: 317, day: "yes", img: '5.png', ],	// DAY - Light sleet
        [code: 320, day: "yes", img: '6.png', ],	// DAY - Moderate or heavy sleet
        [code: 323, day: "yes", img: '41.png', ],	// DAY - Patchy light snow
        [code: 326, day: "yes", img: '18.png', ],	// DAY - Light snow
        [code: 329, day: "yes", img: '41.png', ],	// DAY - Patchy moderate snow
        [code: 332, day: "yes", img: '16.png', ],	// DAY - Moderate snow
        [code: 335, day: "yes", img: '41.png', ],	// DAY - Patchy heavy snow
        [code: 338, day: "yes", img: '16.png', ],	// DAY - Heavy snow
        [code: 350, day: "yes", img: '18.png', ],	// DAY - Ice pellets
        [code: 353, day: "yes", img: '11.png', ],	// DAY - Light rain shower
        [code: 356, day: "yes", img: '12.png', ],	// DAY - Moderate or heavy rain shower
        [code: 359, day: "yes", img: '12.png', ],	// DAY - Torrential rain shower
        [code: 362, day: "yes", img: '5.png', ],	// DAY - Light sleet showers
        [code: 365, day: "yes", img: '6.png', ],	// DAY - Moderate or heavy sleet showers
        [code: 368, day: "yes", img: '16.png', ],	// DAY - Light snow showers
        [code: 371, day: "yes", img: '16.png', ],	// DAY - Moderate or heavy snow showers
        [code: 374, day: "yes", img: '8.png', ],	// DAY - Light showers of ice pellets
        [code: 377, day: "yes", img: '10.png', ],	// DAY - Moderate or heavy showers of ice pellets
        [code: 386, day: "yes", img: '38.png', ],	// DAY - Patchy light rain with thunder
        [code: 389, day: "yes", img: '35.png', ],	// DAY - Moderate or heavy rain with thunder
        [code: 392, day: "yes", img: '41.png', ],	// DAY - Patchy light snow with thunder
        [code: 395, day: "yes", img: '18.png', ],	// DAY - Moderate or heavy snow with thunder
        [code: 113, day: "no", img: '31.png', ],	// NIGHT - Clear
        [code: 116, day: "no", img: '29.png', ],	// NIGHT - Partly cloudy
        [code: 119, day: "no", img: '27.png', ],	// NIGHT - Cloudy
        [code: 122, day: "no", img: '26.png', ],	// NIGHT - Overcast
        [code: 143, day: "no", img: '20.png', ],	// NIGHT - Mist
        [code: 176, day: "no", img: '45.png', ],	// NIGHT - Patchy rain possible
        [code: 179, day: "no", img: '46.png', ],	// NIGHT - Patchy snow possible
        [code: 182, day: "no", img: '46.png', ],	// NIGHT - Patchy sleet possible
        [code: 185, day: "no", img: '45.png', ],	// NIGHT - Patchy freezing drizzle possible
        [code: 200, day: "no", img: '47.png', ],	// NIGHT - Thundery outbreaks possible
        [code: 227, day: "no", img: '15.png', ],	// NIGHT - Blowing snow
        [code: 230, day: "no", img: '16.png', ],	// NIGHT - Blizzard
        [code: 248, day: "no", img: '21.png', ],	// NIGHT - Fog
        [code: 260, day: "no", img: '21.png', ],	// NIGHT - Freezing fog
        [code: 263, day: "no", img: '45.png', ],	// NIGHT - Patchy light drizzle
        [code: 266, day: "no", img: '11.png', ],	// NIGHT - Light drizzle
        [code: 281, day: "no", img: '8.png', ],	// NIGHT - Freezing drizzle
        [code: 284, day: "no", img: '10.png', ],	// NIGHT - Heavy freezing drizzle
        [code: 293, day: "no", img: '45.png', ],	// NIGHT - Patchy light rain
        [code: 296, day: "no", img: '11.png', ],	// NIGHT - Light rain
        [code: 299, day: "no", img: '45.png', ],	// NIGHT - Moderate rain at times
        [code: 302, day: "no", img: '12.png', ],	// NIGHT - Moderate rain
        [code: 305, day: "no", img: '45.png', ],	// NIGHT - Heavy rain at times
        [code: 308, day: "no", img: '12.png', ],	// NIGHT - Heavy rain
        [code: 311, day: "no", img: '8.png', ],	// NIGHT - Light freezing rain
        [code: 314, day: "no", img: '10.png', ],	// NIGHT - Moderate or heavy freezing rain
        [code: 317, day: "no", img: '5.png', ],	// NIGHT - Light sleet
        [code: 320, day: "no", img: '6.png', ],	// NIGHT - Moderate or heavy sleet
        [code: 323, day: "no", img: '41.png', ],	// NIGHT - Patchy light snow
        [code: 326, day: "no", img: '18.png', ],	// NIGHT - Light snow
        [code: 329, day: "no", img: '41.png', ],	// NIGHT - Patchy moderate snow
        [code: 332, day: "no", img: '16.png', ],	// NIGHT - Moderate snow
        [code: 335, day: "no", img: '41.png', ],	// NIGHT - Patchy heavy snow
        [code: 338, day: "no", img: '16.png', ],	// NIGHT - Heavy snow
        [code: 350, day: "no", img: '18.png', ],	// NIGHT - Ice pellets
        [code: 353, day: "no", img: '11.png', ],	// NIGHT - Light rain shower
        [code: 356, day: "no", img: '12.png', ],	// NIGHT - Moderate or heavy rain shower
        [code: 359, day: "no", img: '12.png', ],	// NIGHT - Torrential rain shower
        [code: 362, day: "no", img: '5.png', ],	// NIGHT - Light sleet showers
        [code: 365, day: "no", img: '6.png', ],	// NIGHT - Moderate or heavy sleet showers
        [code: 368, day: "no", img: '16.png', ],	// NIGHT - Light snow showers
        [code: 371, day: "no", img: '16.png', ],	// NIGHT - Moderate or heavy snow showers
        [code: 374, day: "no", img: '8.png', ],	// NIGHT - Light showers of ice pellets
        [code: 377, day: "no", img: '10.png', ],	// NIGHT - Moderate or heavy showers of ice pellets
        [code: 386, day: "no", img: '47.png', ],	// NIGHT - Patchy light rain with thunder
        [code: 389, day: "no", img: '35.png', ],	// NIGHT - Moderate or heavy rain with thunder
        [code: 392, day: "no", img: '46.png', ],	// NIGHT - Patchy light snow with thunder
        [code: 395, day: "no", img: '18.png', ]	// NIGHT - Moderate or heavy snow with thunder
]

@Field final Map	attributesMap = [
	city:				'City',
	cloud:				'Cloud',
	cCF:				'Cloud cover factor',
	condition_code:		'Condition code',
	condition_icon:		'Condition icon',
	condition_icon_url:	'Condition icon URL',
	condition_text:		'Condition text',
	weather:			'Condition text',
	country:			'Country',
	feelsLike:			'Feels like (in default unit)',
	humidity:			'Humidity',
	illuminance:		'Illuminance',
	illuminated:		'Dashboard illuminance',
	is_day:				'Is daytime',
	localtime_epoch:	'Localtime epoch',
	local_date:			'Local date',
	localSunrise:		'Local sunrise',
	local_sunrise:		'Local sunrise',
	localSunset:		'Local sunset',
	local_sunset:		'Local sunset',
	twilight_begin:		'Twilight begin',
	twilight_end:		'Twilight end',
	local_time:			'Local time',
	tz_id:				'Timezone ID',
	name:				'Location name',
	region:				'Region',
	location:			'Location name with region',
	lon:				'Longitude',
	lat:				'Latitude',
	last_updated:		'Last updated',
	last_updated_epoch:	'Last updated epoch',
	mytile:				'Mytile for dashboard',
	precip:			    'Precipitation in default units',
	pressure:			'Pressure',
	temperature:		'Temperature',
    visibility:         'Visibility in default units' ,
	visual:				'Visual weather',
	visualWithText:		'Visual weather with text',
	wind:				'Wind (in default unit)',
	wind_degree:		'Wind Degree',
	wind_kph:			'Wind KPH',
	wind_mph:			'Wind MPH',
	wind_mps:			'Wind MPS',
	wind_degree:		'Wind degree',
	wind_dir:			'Wind direction',
	wind_mytile:		'Wind mytile'
]

//**********************************************************************************************************************
