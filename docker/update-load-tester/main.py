import threading
import urllib2
import time
import logging
import Queue

logging.basicConfig(level=logging.DEBUG,
                    format='[%(levelname)s] (%(threadName)-10s) %(message)s',
                    )

conf_thread_count = 32
conf_url = 'http://localhost:19180/UpdateService/2.0'

with open('files/header.txt', 'r') as f_h:
    header = f_h.read()
f_h.close()

with open('files/bodies.txt', 'r') as f_b:
    bodies = f_b.readlines()
f_b.close()

with open('files/footer.txt', 'r') as f_f:
    footer = f_f.read()
f_f.close()


def call_updateservice(body_slice):
    ok_reply = '<ns1:updateStatus>ok</ns1:updateStatus>'

    logging.info('Starting')

    count = 0
    execute_time = 0

    for body in body_slice:
        request = header + body + footer

        start_time = time.time()
        req = urllib2.Request(conf_url, request, headers)
        response = urllib2.urlopen(req, timeout=60)
        html_string = response.read()

        if not ok_reply in html_string:
            logging.info("Bad response: " + html_string)

        execute_time += time.time() - start_time

        #logging.info('Completed call in: %s' % (time.time() - start_time))
        count += 1
        logging.info(get_progress_dots(count % 100))
        if count % 10 == 0:
            logging.info('After %s calls the average execution time is %s' % (count, (execute_time / count)))

    logging.info('STOPPING!')

def get_progress_dots(count):
    progress = ''
    for c in range(count):
        progress += '.'

    return progress


headers = {
    'Content-Type': 'text/xml; charset=utf-8'
}

print 'Number of lines', len(bodies)

threads = []

print "Starting threads..."

slice_size = len(bodies) / conf_thread_count

for t in range(conf_thread_count):
    body_slice = bodies[t * slice_size:((t + 1) * slice_size) - 1]
    print len(body_slice)
    t = threading.Thread(name='thread-' + str(t), target=call_updateservice, args=([body_slice]))
    t.daemon = True
    threads.append(t)

for t in threads:
    t.start()

for t in threads:
    t.join()
