# A simple Python script to connect to the WebSocket server and print received messages.
#
# Installation:
# 1. Make sure you have Python installed on your system.
# 2. Install the required library using pip:
#    pip install websocket-client
#
# How to run:
# 1. Find your phone's local IP address displayed in the BTAndroidApp.
# 2. Replace the `YOUR_PHONE_IP_ADDRESS` placeholder in the `ws_url` variable below with your phone's actual IP.
# 3. Run the script from your terminal:
#    python websocket_client.py
#

import websocket
import json
import time

def on_message(ws, message):
    """Callback function to handle incoming messages."""
    try:
        data = json.loads(message)
        print("--- New Message ---")
        print(f"  Device Name: {data.get('deviceName', 'N/A')}")
        print(f"  Device Address: {data.get('deviceAddress', 'N/A')}")
        print(f"  Service UUID: {data.get('serviceUUID', 'N/A')}")
        print(f"  Characteristic UUID: {data.get('characteristicUUID', 'N/A')}")
        print(f"  Raw Value: {data.get('raw_value', 'N/A')}")
        if data.get('parsed_value'):
            print(f"  Parsed Value: {data.get('parsed_value')}")
        print("---------------------\\n")
    except json.JSONDecodeError:
        print(f"Received non-JSON message: {message}")

def on_error(ws, error):
    """Callback function to handle errors."""
    print(f"### Error: {error} ###")

def on_close(ws, close_status_code, close_msg):
    """Callback function to handle connection closing."""
    print("### Connection closed ###")

def on_open(ws):
    """Callback function when the connection is opened."""
    print("### Connection opened ###")

def connect_websocket():
    """Creates and runs the WebSocket connection."""
    # --- IMPORTANT ---
    # Replace this with the IP address shown in the Android app.
    phone_ip = "YOUR_PHONE_IP_ADDRESS"
    phone_ip = "192.168.18.66"
    # -----------------

    ws_url = f"ws://{phone_ip}:8080/ws"
    print(f"Attempting to connect to {ws_url}...")

    ws = websocket.WebSocketApp(ws_url,
                              on_open=on_open,
                              on_message=on_message,
                              on_error=on_error,
                              on_close=on_close)
    ws.run_forever()

if __name__ == "__main__":
    while True:
        try:
            connect_websocket()
        except Exception as e:
            print(f"An exception occurred: {e}. Reconnecting in 5 seconds...")
        time.sleep(5)
