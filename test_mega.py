import urllib.request
import json
import base64

folder_id = "Io4myLQC"
key_b64 = "0MV-ZU9NXIQZtRfcKSiqog"
sub_folder = "1hogxZ7B"

url = f"https://g.mega.co.nz/cs?id=123456&n={folder_id}"
payload = json.dumps([{"a": "f", "c": 1, "r": 1}]).encode('utf-8')

req = urllib.request.Request(url, data=payload, headers={'Content-Type': 'application/json'})
try:
    with urllib.request.urlopen(req) as response:
        res = response.read().decode('utf-8')
        print("Response status:", response.status)
        print("Response length:", len(res))
        data = json.loads(res)
        print("Type of data:", type(data))
        if isinstance(data, list) and len(data) > 0:
            first = data[0]
            print("First item keys:", first.keys() if isinstance(first, dict) else first)
            if isinstance(first, dict) and "f" in first:
                files = first["f"]
                print("Total files/nodes in folder:", len(files))
                for f in files[:5]:
                    print("Sample node:", f)
            else:
                print("No 'f' key, returned:", first)
        else:
            print("Unexpected response:", data)
except Exception as e:
    print("Error:", e)

