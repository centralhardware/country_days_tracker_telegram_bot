function otr_init()
end

function otr_hook(topic, _type, data)
        -- Log all keys available in the data table
        if data ~= nil then
                for key, value in pairs(data) do
                        otr.log("data key: " .. tostring(key) .. ", value: " .. tostring(value))
                end
        else
                otr.log("data is nil")
        end

        -- Escapes string values for JSON
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

        -- Convert numeric values to strings and fallback to 0 when nil
        local function escape_json_number(num)
                if num == nil then return "0" end
                return tostring(num)
        end

        local json = '{' ..
                        '"latitude":'  .. escape_json_number(data['lat']) .. ',' ..
                        '"longitude":' .. escape_json_number(data['lon']) .. ',' ..
                        '"timestamp":' .. escape_json_number(data['tst']) .. ',' ..
                        '"timezone":"'  .. escape_json(data['tzname']) .. '",' ..
                        '"country":"'   .. escape_json(data['cc']) .. '",' ..
                        '"alt":'       .. escape_json_number(data['alt']) .. ',' ..
                        '"batt":'      .. escape_json_number(data['batt']) .. ',' ..
                        '"acc":'       .. escape_json_number(data['acc']) .. ',' ..
                        '"vac":'       .. escape_json_number(data['vac']) .. ',' ..
                        '"conn":"'      .. escape_json(data['conn']) .. '",' ..
                        '"locality":"'  .. escape_json(data['locality']) .. '",' ..
                        '"ghash":"'     .. escape_json(data['ghash']) .. '",' ..
                        '"p":'         .. escape_json_number(data['p']) .. ',' ..
                        '"addr":"'      .. escape_json(data['addr']) .. '",' ..
                        '"bssid":"'     .. escape_json(data['bssid']) .. '",' ..
                        '"ssid":"'      .. escape_json(data['ssid']) .. '",' ..
                        '"bs":'         .. escape_json_number(data['bs']) ..
                        '}'

        -- Escape single quotes so the JSON can be safely wrapped in single
        -- quotes when passed to the shell command below.
        local json_escaped = json:gsub("'", "'\\''")

	otr.log("Sending data as JSON:")
	otr.log("Payload: " .. json)

	local url = "http://10.168.0.77:7890/location"
        local cmd = 'curl -X POST -s "' .. url .. '" -H "Content-Type: application/json" -d \'' .. json_escaped .. '\' 2>&1'
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
