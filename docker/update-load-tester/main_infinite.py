import threading
import urllib2
import time
import logging
import random
import socket
import httplib

logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s %(levelname)s [%(threadName)-10s] %(message)s',
                    )

conf_thread_count = 32

# Version 2.0
# conf_url = 'http://localhost:19180/UpdateService/2.0'

# conf_url = 'http://devel8:59180/UpdateService/2.0'
conf_url = 'http://javaws-p04:33180/CatalogingUpdateServices/UpdateService'

# Version 1.0
# conf_url = 'http://ifish-i02:19180/CatalogingUpdateServices/UpdateService'

headers = {
    'Content-Type': 'text/xml; charset=utf-8'
}

threads = []

with open('files/header.txt', 'r') as f_h:
    header = f_h.read()

with open('files/bodies.txt', 'r') as f_b:
    bodies = f_b.readlines()

with open('files/footer.txt', 'r') as f_f:
    footer = f_f.read()


def call_updateservice(body_slice):
    ok_reply = '<updateStatus>ok</updateStatus>'
    #ok_reply = '<ns1:updateStatus>ok</ns1:updateStatus>'

    logging.info('THREAD STARTING!')

    count_loop = 1
    count = 0
    count_alive = 0
    count_ok = 0
    count_failed = 0
    count_timeout = 0
    count_exceptions = 0

    execute_time = 0
    execute_time_since_last = 0

    do_continue = True

    while do_continue:
        logging.info('Starting loop %s' % (count_loop))
        loop_start_time = time.time()

        for body in body_slice:
            request = header + body + footer

            start_time = time.time()
            req = urllib2.Request(conf_url, request, headers)
            try:
                response = urllib2.urlopen(req, timeout=120)
                html_string = response.read()

                if ok_reply not in html_string:
                    #logging.info("Bad response: " + html_string)
                    count_failed += 1
                else:
                    count_ok += 1
            except socket.timeout, e:
                count_timeout += 1
                logging.error('Got time out')
            except httplib.BadStatusLine, e:
                count_exceptions += 1
                logging.error('Got BadStatusLine')


            execute_time += time.time() - start_time
            execute_time_since_last += time.time() - start_time

            count += 1
            count_alive += 1

            if random.randint(1, 20) == 1:
                #logging.info('After %s (%s OK, %s FAILED) calls the total average execution time is %s seconds. The execution time since last report was %s seconds' % (count, count_ok, count_failed, round(execute_time / count, 2), round(execute_time_since_last/count_alive, 2)))
                logging.info('WS call summary: %s total / %s ok / %s failed / %s timeout / %s exceptions - Total average execute time: %s - Recent average execute time: %s' % (count, count_ok, count_failed, count_timeout, count_exceptions, round(execute_time / count, 2), round(execute_time_since_last/count_alive, 2)))
                execute_time_since_last = 0
                count_alive = 0

        logging.info('Loop %s done. Execute time was %s seconds' % (count_loop, round(time.time() - loop_start_time, 2)))
        count_loop += 1

        #do_continue = False

    logging.info('STOPPING!')

print "Starting threads..."

#bodies = [bodies[0]]

slice_size = len(bodies) / conf_thread_count
#print 'Slize size', slice_size

for t in range(conf_thread_count):
    body_slice = bodies[t * slice_size:((t + 1) * slice_size)]

    t = threading.Thread(name='thread-' + str(t), target=call_updateservice, args=([body_slice]))
    t.daemon = True
    threads.append(t)

for t in threads:
    t.start()

for t in threads:
    t.join()
