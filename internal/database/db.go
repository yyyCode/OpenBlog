package database

import (
	"fmt"
	"log"
	"openblog/internal/config"
	"openblog/internal/models"

	"gorm.io/driver/mysql"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

var DB *gorm.DB

func InitDB() {
	var err error
	dbConfig := config.AppConfig.Database

	switch dbConfig.Driver {
	case "mysql":
		dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
			dbConfig.User,
			dbConfig.Password,
			dbConfig.Host,
			dbConfig.Port,
			dbConfig.DBName,
		)
		DB, err = gorm.Open(mysql.Open(dsn), &gorm.Config{})
	case "sqlite":
		DB, err = gorm.Open(sqlite.Open(dbConfig.File), &gorm.Config{})
	default:
		log.Fatal("Unsupported database driver:", dbConfig.Driver)
	}

	if err != nil {
		log.Fatal("Failed to connect to database:", err)
	}

	// Auto Migrate
	err = DB.AutoMigrate(&models.Post{}, &models.User{})
	if err != nil {
		log.Fatal("Failed to migrate database:", err)
	}
}
