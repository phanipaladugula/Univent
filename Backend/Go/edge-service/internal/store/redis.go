package store

import (
	"context"
	"fmt"
	"log"

	"github.com/redis/go-redis/v9"
)

type RedisStore struct {
	Client *redis.Client
}

func NewRedisStore(redisURL string) (*RedisStore, error) {
	opts, err := redis.ParseURL(redisURL)
	if err != nil {
		return nil, fmt.Errorf("parse redis URL: %w", err)
	}

	client := redis.NewClient(opts)

	if err := client.Ping(context.Background()).Err(); err != nil {
		return nil, fmt.Errorf("ping redis: %w", err)
	}

	log.Println("✅ Connected to Redis")
	return &RedisStore{Client: client}, nil
}

func (s *RedisStore) Close() error {
	return s.Client.Close()
}

func (s *RedisStore) Ping(ctx context.Context) error {
	return s.Client.Ping(ctx).Err()
}
