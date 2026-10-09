-- Demo accounts (all use the password "tutor123"; hashes are BCrypt).
INSERT INTO users(user_id,full_name,email,password_hash,role) VALUES
 (1,'Maya Student','maya@example.test','$2a$10$emm5VMyzxjPHrBbzHiVbW.QtBsDvcUA1bsXh0OkSmMbhz34H0JgtK','CUSTOMER'),
 (2,'Alex Chen','alex@example.test','$2a$10$IAsKz/.DM6oQyxwWUCsi2.eGFdSSAi3MT99exNqpvdkuplH9POxgG','PROVIDER'),
 (3,'Sam Rivera','sam@example.test','$2a$10$VyeJEESi9GfYsXPSni8z/OeSjwRHpGB75xZ3andIaOAwJZimrFnsm','PROVIDER'),
 (4,'Jordan Lee','jordan@example.test','$2a$10$jU.unwEqTsT0UH4xztYqsOCbTwa4KXkLuM3sVGi1VkPkS0Xu/TFp6','CUSTOMER');
INSERT INTO providers(provider_id,user_id,bio) VALUES
 (1,2,'Java and software design tutor'), (2,3,'Databases and SQL tutor');
INSERT INTO services(service_id,name,description) VALUES
 (1,'Java Fundamentals','One-hour Java tutoring'),
 (2,'SQL and Databases','One-hour SQL tutoring');
-- Relative dates keep the sample useful whenever the application is started.
-- Slot 5 is yesterday, so Maya's booking on it shows up as COMPLETED.
INSERT INTO availability_slots(slot_id,provider_id,service_id,starts_at,ends_at) VALUES
 (1,1,1,DATEADD('HOUR',10,DATEADD('DAY',1,CURRENT_DATE)),DATEADD('HOUR',11,DATEADD('DAY',1,CURRENT_DATE))),
 (2,1,1,DATEADD('HOUR',11,DATEADD('DAY',1,CURRENT_DATE)),DATEADD('HOUR',12,DATEADD('DAY',1,CURRENT_DATE))),
 (3,2,2,DATEADD('HOUR',10,DATEADD('DAY',1,CURRENT_DATE)),DATEADD('HOUR',11,DATEADD('DAY',1,CURRENT_DATE))),
 (4,2,2,DATEADD('HOUR',14,DATEADD('DAY',2,CURRENT_DATE)),DATEADD('HOUR',15,DATEADD('DAY',2,CURRENT_DATE))),
 (5,1,1,DATEADD('HOUR',10,DATEADD('DAY',-1,CURRENT_DATE)),DATEADD('HOUR',11,DATEADD('DAY',-1,CURRENT_DATE)));
INSERT INTO appointments(appointment_id,customer_id,slot_id,status) VALUES (1,1,2,'BOOKED'),(2,1,5,'BOOKED');
-- Explicit fixture IDs must not collide with later generated IDs.
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 5;
ALTER TABLE providers ALTER COLUMN provider_id RESTART WITH 3;
ALTER TABLE services ALTER COLUMN service_id RESTART WITH 3;
ALTER TABLE availability_slots ALTER COLUMN slot_id RESTART WITH 6;
ALTER TABLE appointments ALTER COLUMN appointment_id RESTART WITH 3;
