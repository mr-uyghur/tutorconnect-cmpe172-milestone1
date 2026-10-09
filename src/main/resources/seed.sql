-- Sample accounts use BCrypt hashes for the demonstration password "tutor123".
-- DatabaseInitializer runs this script only for a new database.
INSERT OR IGNORE INTO users(user_id,full_name,email,password_hash,role) VALUES
 (1,'Maya Student','maya@example.test','$2a$10$emm5VMyzxjPHrBbzHiVbW.QtBsDvcUA1bsXh0OkSmMbhz34H0JgtK','CUSTOMER'),
 (2,'Alex Chen','alex@example.test','$2a$10$IAsKz/.DM6oQyxwWUCsi2.eGFdSSAi3MT99exNqpvdkuplH9POxgG','PROVIDER'),
 (3,'Sam Rivera','sam@example.test','$2a$10$VyeJEESi9GfYsXPSni8z/OeSjwRHpGB75xZ3andIaOAwJZimrFnsm','PROVIDER'),
 (4,'Jordan Lee','jordan@example.test','$2a$10$jU.unwEqTsT0UH4xztYqsOCbTwa4KXkLuM3sVGi1VkPkS0Xu/TFp6','CUSTOMER');
INSERT OR IGNORE INTO providers(provider_id,user_id,bio) VALUES
 (1,2,'Java and software design tutor'), (2,3,'Databases and SQL tutor');
INSERT OR IGNORE INTO services(service_id,name,description) VALUES
 (1,'Java Fundamentals','One-hour Java tutoring'),
 (2,'SQL and Databases','One-hour SQL tutoring');
-- Dates are calculated on first startup; bookings and user changes then persist.
INSERT OR IGNORE INTO availability_slots(slot_id,provider_id,service_id,starts_at,ends_at) VALUES
 (1,1,1,datetime(date('now','localtime'),'+1 day','+10 hours'),datetime(date('now','localtime'),'+1 day','+11 hours')),
 (2,1,1,datetime(date('now','localtime'),'+1 day','+11 hours'),datetime(date('now','localtime'),'+1 day','+12 hours')),
 (3,2,2,datetime(date('now','localtime'),'+1 day','+10 hours'),datetime(date('now','localtime'),'+1 day','+11 hours')),
 (4,2,2,datetime(date('now','localtime'),'+2 days','+14 hours'),datetime(date('now','localtime'),'+2 days','+15 hours')),
 (5,1,1,datetime(date('now','localtime'),'-1 day','+10 hours'),datetime(date('now','localtime'),'-1 day','+11 hours'));
INSERT OR IGNORE INTO appointments(appointment_id,customer_id,slot_id,status) VALUES
 (1,1,2,'BOOKED'),(2,1,5,'BOOKED');
