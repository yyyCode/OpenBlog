package main

import (
	"log"
	"openblog/internal/config"
	"openblog/internal/database"
	"openblog/internal/handlers"
	"openblog/internal/middleware"
	"openblog/internal/models"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

func seedAdmin() {
	var count int64
	database.DB.Model(&models.User{}).Count(&count)
	if count == 0 {
		hashedPassword, _ := bcrypt.GenerateFromPassword([]byte("admin123"), bcrypt.DefaultCost)
		admin := models.User{
			Username: "admin",
			Password: string(hashedPassword),
		}
		database.DB.Create(&admin)
		log.Println("Default admin account created: admin / admin123")
	}
}

func main() {
	log.Println("Starting server...")

	// Load Configuration
	config.LoadConfig()

	// Initialize Database
	database.InitDB()
	seedAdmin()

	// Initialize Router
	r := gin.Default()

	// Simple CORS middleware
	r.Use(func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	})

	// Routes
	api := r.Group("/api")
	{
		api.GET("/posts", handlers.GetPosts)
		api.GET("/posts/:id", handlers.GetPost)
		api.POST("/login", handlers.Login)

		// Protected routes
		protected := api.Group("/")
		protected.Use(middleware.AuthMiddleware())
		{
			protected.POST("/posts", handlers.CreatePost)
		}
	}

	// Add a simple welcome route
	r.GET("/", func(c *gin.Context) {
		c.JSON(200, gin.H{
			"message": "Welcome to OpenBlog API",
			"endpoints": []string{
				"GET /api/posts",
				"GET /api/posts/:id",
				"POST /api/login",
				"POST /api/posts (Auth required)",
			},
		})
	})

	// Run Server
	port := config.AppConfig.Server.Port
	if port == "" {
		port = "8080"
	}

	log.Printf("Server is running on http://localhost:%s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatal("Server failed to start:", err)
	}
}
