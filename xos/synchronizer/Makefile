synchronizer:
	cp ../vtnnetport.py tmp.vtnnetport.py
	sudo docker build --rm -f Dockerfile.synchronizer -t xosproject/vtn-synchronizer .

up:
	docker-compose -p vtn up -d
