#!/usr/bin/python3
# -*- coding: utf-8 -*-

import json,time,requests,hashlib,random

host = "http://127.0.0.1:8180/"
# host = "http://192.168.20.6:8180/"

GHeaders = {
    "Content-Type":"application/json",
}

def add_user(uid):
    path = "syncAdminApi/v1/users"
    data = {
        "uid": uid,
    }
    hr = requests.post(host + path,headers = GHeaders,data = json.dumps(data))
    print(hr.text)

def add_assets(code,aid,scale):
    path = "syncAdminApi/v1/assets"
    data = {
        "assetCode":code,
        "assetId":aid,
        "scale":scale,
    }
    hr = requests.post(host + path,headers = GHeaders,data = json.dumps(data))
    print(hr.text)

def init():
    add_user(1)
    add_user(2)
    add_assets("BTC",1,6)
    add_assets("USDT",2,6)
    add_symbols()
    add_money(1,"BTC",6)
    add_money(1,"USDT",6)
    add_money(2,"BTC",6)
    add_money(2,"USDT",6)

def add_symbols():
    path = "syncAdminApi/v1/symbols"
    data = {
        "symbolId": 1,
        "symbolCode":"BTCUSDT",
        "symbolType":0,
        "baseAsset":"BTC",
        "quoteCurrency":"USDT",
        "lotSize":1,
        "stepSize":1,
        "takerFee":0,
        "makerFee":0,
        "marginBuy":0,
        "marginSell":0,
        "priceHighLimit":10000000,
        "priceLowLimit":0.000001,
    }
    hr = requests.post(host + path,headers = GHeaders,data = json.dumps(data))
    print(hr.text)

def add_money(uid,currency,scale):
    path = "syncAdminApi/v1/users/{0}/accounts".format(uid)
    data = {
        "transactionId": random.randint(1,10000),
        "amount":100000 * pow(10,scale),
        "currency":currency,
    }
    hr = requests.post(host + path,headers = GHeaders,data = json.dumps(data))
    print(hr.text)

def user_state(uid):
    path = "syncTradeApi/v1/users/{0}/state".format(uid)
    hr = requests.get(host + path,headers = GHeaders)
    print(hr.text)

def get_info():
    path = "syncTradeApi/v1/info"
    hr = requests.get(host + path,headers = GHeaders)
    print(hr.text)

def get_orderbook(symbol):
    path = "syncTradeApi/v1/symbols/{0}/orderbook?depth={1}".format(symbol,10)
    hr = requests.get(host + path,headers = GHeaders)
    print(hr.text)

def place_order(uid,symbol,action):
    path = "syncTradeApi/v1/symbols/{0}/trade/{1}/orders".format(symbol,uid)
    data = {
        "price": 8010,
        "size": 5,
        "userCookie":uid,
        "action":action, #0-ask 1-bid
        "orderType": 0, #GTC-0,IOC-1
    }
    hr = requests.post(host + path,headers = GHeaders,data = json.dumps(data))
    print(hr.text)

def main():
    symbol = "BTCUSDT"
    # init()
    # user_state(1)
    # get_info()
    get_orderbook(symbol)
    # place_order(2,symbol,0)


if __name__ == '__main__':
    main()
