function otr_init()
end

function otr_hook(topic, _type, data)
	-- Функция экранирования строк для JSON
	local function escape_json(str)
		if str == nil then return "" end
		str = tostring(str)
		str = str:gsub('\\', '\\\\')
		str = str:gsub('"', '\\"')
		str = str:gsub('\n', '\\n')
		str = str:gsub('\r', '\\r')
		str = str:gsub('\t', '\\t')
		return str
	end

	local json = '{' ..
			'"latitude":"'  .. escape_json(data['lat']) .. '",' ..
			'"longitude":"' .. escape_json(data['lon']) .. '",' ..
			'"timezone":"'  .. escape_json(data['tzname']) .. '",' ..
			'"country":"'   .. escape_json(data['cc']) .. '",' ..
			'"alt":"'       .. escape_json(data['alt']) .. '",' ..
			'"batt":"'      .. escape_json(data['batt']) .. '",' ..
			'"acc":"'       .. escape_json(data['acc']) .. '",' ..
			'"vac":"'       .. escape_json(data['vac']) .. '",' ..
			'"conn":"'      .. escape_json(data['conn']) .. '",' ..
			'"locality":"'  .. escape_json(data['locality']) .. '",' ..
			'"ghash":"'     .. escape_json(data['ghash']) .. '",' ..
			'"p":"'         .. escape_json(data['p']) .. '",' ..
			'"addr":"'      .. escape_json(data['addr']) .. '"' ..
			'}'

	otr.log("Sending data as JSON:")
	otr.log("Payload: " .. json)

	local url = "http://10.168.0.77:7890/location"
	local cmd = 'curl -X POST -s "' .. url .. '" -H "Content-Type: application/json" -d \'' .. json .. '\' 2>&1'
	otr.log("Executing command: " .. cmd)

	local handle = io.popen(cmd)
	handle:read("*a")
	local success, exit_code = handle:close()

	if success then
		otr.log("Curl command executed successfully")
	else
		otr.log("Curl command failed with exit code: " .. (exit_code or "unknown"))
	end
end

function otr_exit()
end
