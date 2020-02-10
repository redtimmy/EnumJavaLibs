#!/usr/bin/env python3
# Author: Stefan Broeder <5umm1t@protonmail.com>
#
# Requirements: pip install beautifulsoup4

from bs4 import BeautifulSoup
import sys
import requests

BASE_URL = "https://mvnrepository.com"

def getPopularJars(nr_jars):
    pages = int(nr_jars / 10)
    for page in range(pages): 
        r = requests.get(BASE_URL +  "/popular?p=" + str(page + 1))
        soup = BeautifulSoup(r.text, 'html.parser')
        for div in soup.findAll("div", {"class": "im"}):
            artifact = div.a.get("href")
            getLatestJar(artifact)

def getLatestJar(artifact):
    r = requests.get(BASE_URL + artifact + "/latest")
    soup = BeautifulSoup(r.text, 'html.parser')
    for link in soup.find_all("a"):
        if link.get('href').endswith(".jar"):
            print(link.get("href"))

def main():
    if len(sys.argv) < 2:
        print("Usage: " + sys.argv[0] + " {nr_of_jars}");
        sys.exit(1)

    nr_jars = int(sys.argv[1])
    getPopularJars(nr_jars)

main()
