USE cache_test;

-- Clear existing data
DELETE FROM orders;
DELETE FROM product;

-- Reset auto-increment
ALTER TABLE product AUTO_INCREMENT = 1;
ALTER TABLE orders AUTO_INCREMENT = 1;

-- Generate 100,000 products
DELIMITER //
CREATE PROCEDURE GenerateProducts()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE p_name VARCHAR(255);
    DECLARE p_price BIGINT;
    DECLARE p_stock INT;
    DECLARE p_category VARCHAR(50);
    DECLARE p_created_at DATETIME;

    WHILE i < 100000 DO
        SET p_name = CONCAT('Product ', i + 1);
        SET p_price = FLOOR(1000 + (RAND() * 99000)); -- 1,000 to 100,000
        SET p_stock = FLOOR(10 + (RAND() * 990));    -- 10 to 1,000

        CASE FLOOR(RAND() * 4)
            WHEN 0 THEN SET p_category = 'ELECTRONICS';
            WHEN 1 THEN SET p_category = 'FASHION';
            WHEN 2 THEN SET p_category = 'FOOD';
            WHEN 3 THEN SET p_category = 'BOOK';
        END CASE;

        SET p_created_at = NOW() - INTERVAL FLOOR(RAND() * 365) DAY - INTERVAL FLOOR(RAND() * 24) HOUR;

        INSERT INTO product (name, price, stock, category, created_at)
        VALUES (p_name, p_price, p_stock, p_category, p_created_at);

        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL GenerateProducts();
DROP PROCEDURE GenerateProducts;

-- Generate 50,000 orders
DELIMITER //
CREATE PROCEDURE GenerateOrders()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE o_user_id BIGINT;
    DECLARE o_product_id BIGINT;
    DECLARE o_quantity INT;
    DECLARE o_total_amount BIGINT;
    DECLARE o_status VARCHAR(50);
    DECLARE o_created_at DATETIME;
    DECLARE p_price BIGINT;

    WHILE i < 50000 DO
        SET o_user_id = FLOOR(1 + (RAND() * 10000)); -- 1 to 10,000
        SET o_product_id = FLOOR(1 + (RAND() * 100000)); -- 1 to 100,000
        SET o_quantity = FLOOR(1 + (RAND() * 5)); -- 1 to 5

        -- Get product price to calculate total_amount
        SELECT price INTO p_price FROM product WHERE id = o_product_id;
        SET o_total_amount = p_price * o_quantity;

        IF RAND() < 0.9 THEN
            SET o_status = 'PAID';
        ELSE
            SET o_status = 'PENDING';
        END IF;

        SET o_created_at = NOW() - INTERVAL FLOOR(RAND() * 365) DAY - INTERVAL FLOOR(RAND() * 24) HOUR;

        INSERT INTO orders (user_id, product_id, quantity, total_amount, status, created_at)
        VALUES (o_user_id, o_product_id, o_quantity, o_total_amount, o_status, o_created_at);

        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL GenerateOrders();
DROP PROCEDURE GenerateOrders;
