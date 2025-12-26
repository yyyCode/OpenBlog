package handlers

import (
	"net/http"
	"openblog/internal/database"
	"openblog/internal/models"

	"github.com/gin-gonic/gin"
)

// CreatePost creates a new post
func CreatePost(c *gin.Context) {
	// Authentication is handled by AuthMiddleware

	var post models.Post
	if err := c.ShouldBindJSON(&post); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if err := database.DB.Create(&post).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create post"})
		return
	}

	c.JSON(http.StatusCreated, post)
}

// GetPosts retrieves all posts
func GetPosts(c *gin.Context) {
	var posts []models.Post
	// Return posts ordered by creation date (newest first)
	if err := database.DB.Order("created_at desc").Find(&posts).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch posts"})
		return
	}

	c.JSON(http.StatusOK, posts)
}

// GetPost retrieves a single post by ID
func GetPost(c *gin.Context) {
	id := c.Param("id")
	var post models.Post
	if err := database.DB.First(&post, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Post not found"})
		return
	}

	c.JSON(http.StatusOK, post)
}
