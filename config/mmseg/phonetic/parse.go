package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
)

type Message struct {
	Title  string         `json:"title,omitempty"`
	Explan []*Explanation `json:"heteronyms,omitempty"`
}

type Explanation struct {
	Bopomofo  string `json:"bopomofo,omitempty"`
	Bopomofo2 string `json:"bopomofo2,omitempty"`
	Pinyin    string `json:"pinyin,omitempty"`
}

func main() {
	f, err := os.Open("./dict-revised.json")
	if err != nil {
		log.Fatal(err)
	}

	dec := json.NewDecoder(f)

	// read open bracket
	_, err = dec.Token()
	if err != nil {
		log.Fatal(err)
	}

	// while the array contains values
	for dec.More() {
		var m Message
		// decode an array value (Message)
		err := dec.Decode(&m)
		if err != nil {
			log.Fatal(err)
		}
		fmt.Printf("%v", m.Title)
		for _, t := range m.Explan {
			if t.Bopomofo != "" {
				fmt.Printf("|%v", t.Bopomofo)
			}
			if t.Bopomofo2 != "" {
				fmt.Printf("|%v", t.Bopomofo2)
			}
			if t.Pinyin != "" {
				fmt.Printf("|%v", t.Pinyin)
			}
		}
		fmt.Printf("\n")
	}

	// read closing bracket
	_, err = dec.Token()
	if err != nil {
		log.Fatal(err)
	}
}
