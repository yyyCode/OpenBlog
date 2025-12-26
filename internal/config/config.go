package config

import (
	"log"

	"github.com/spf13/viper"
)

type Config struct {
	Server   ServerConfig   `mapstructure:"server"`
	Database DatabaseConfig `mapstructure:"database"`
}

type ServerConfig struct {
	Port   string `mapstructure:"port"`
	APIKey string `mapstructure:"api_key"`
}

type DatabaseConfig struct {
	Driver   string `mapstructure:"driver"`
	File     string `mapstructure:"file"`
	Host     string `mapstructure:"host"`
	Port     string `mapstructure:"port"`
	User     string `mapstructure:"user"`
	Password string `mapstructure:"password"`
	DBName   string `mapstructure:"dbname"`
}

var AppConfig Config

func LoadConfig() {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("./config")
	viper.AddConfigPath(".")

	if err := viper.ReadInConfig(); err != nil {
		log.Fatal("Error reading config file: ", err)
	}

	if err := viper.Unmarshal(&AppConfig); err != nil {
		log.Fatal("Unable to decode into struct: ", err)
	}
}
