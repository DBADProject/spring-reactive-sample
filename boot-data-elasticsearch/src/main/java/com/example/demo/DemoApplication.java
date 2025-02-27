package com.example.demo;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}

@Component
@Slf4j
@Profile("default")
class DataInitializer implements CommandLineRunner {

    private final PostRepository posts;

    public DataInitializer(PostRepository posts) {
        this.posts = posts;
    }

    @Override
    public void run(String[] args) {
        log.info("start data initialization  ...");
        this.posts
                .deleteAll()
                .thenMany(
                        Flux
                                .just("post1", "post2")
                                .flatMap(
                                        title -> this.posts.save(Post.builder().title(title).content("content of " + Math.random()).build())
                                )
                )
                .log()
                .subscribe(
                        null,
                        null,
                        () -> log.info("done initialization...")
                );
    }
}

@RestController()
@RequestMapping(value = "/posts")
class PostController {

    private final PostRepository posts;

    public PostController(PostRepository posts) {
        this.posts = posts;
    }

    @GetMapping("")
    public Flux<Post> all() {
        return this.posts.findAll();
    }

    @PostMapping("")
    public Mono<Post> create(@RequestBody Post post) {
        return this.posts.save(post);
    }

    @GetMapping("/{id}")
    public Mono<Post> get(@PathVariable("id") String id) {
        return this.posts.findById(id);
    }

    @GetMapping("/search/title/{term}")
    public Flux<Post> searchTitle(@PathVariable("term") String term) {
        return this.posts.findByTitle(term);
    }

    @GetMapping("/search/content/{term}")
    public Flux<Post> searchContent(@PathVariable("term") String term) {
        return this.posts.findByContent(term);
    }

    @GetMapping("/search/{term}")
    public Flux<Post> search(@PathVariable("term") String term) {
        return this.posts.findByQuery(term);
    }

    @PutMapping("/{id}")
    public Mono<Post> update(@PathVariable("id") String id, @RequestBody Post post) {
        return this.posts.findById(id)
                .map(p -> {
                    p.setTitle(post.getTitle());
                    p.setContent(post.getContent());

                    return p;
                })
                .flatMap(this.posts::save);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable("id") String id) {
        return this.posts.deleteById(id);
    }

}

// docker run -p 9200:9200   -e "discovery.type=single-node"   docker.elastic.co/elasticsearch/elasticsearch:7.10.0   -a "elastic_search"

// https://developpaper.com/spring-boot-integrates-elasticsearch/
// https://reflectoring.io/spring-boot-elasticsearch/
// https://hantsy.github.io/spring-reactive-sample/data/data-elasticsearch.html

// https://coralogix.com/blog/42-elasticsearch-query-examples-hands-on-tutorial/
interface PostRepository extends ReactiveElasticsearchRepository<Post, String> {
    @Query("""
            {
                "match": {
                    "title": {
                        "query": "?0"
                    }
                }
            }
            """)
    Flux<Post> findByTitle(String title);

    @Query("""
            {
                "match": {
                    "content": {
                        "query": "?0"
                    }
                }
            }
            """)
    Flux<Post> findByContent(String content);

    @Query("""
            {
                "multi_match": {
                    "query": "?0"
                }
            }
            """)
    Flux<Post> findByQuery(String query);
}

@Document(indexName = "posts")
@Data
@ToString
@Builder
class Post {
    @Id
    private String id;

    @Field(store = true, type = FieldType.Text, fielddata = true)
    private String title;

    @Field(store = true, type = FieldType.Text, fielddata = true)
    private String content;
}