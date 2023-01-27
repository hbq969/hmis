#!/bin/sh

if [[ -f "../setenv.sh" ]];then
. ../setenv.sh
fi

name=monitor
ver=1.0-SNAPSHOT
tag=${name}:${ver}

echo -e "\nStop Docker Containers ..."
cid=`docker ps -a|grep ${name}|grep ${ver}|awk '{print $1}'`
if [[ -n "${cid}" ]]; then
  docker stop ${cid}
  docker rm -f ${cid}
  echo "monitor,${cid} was stop."
fi

echo -e "\nStart Docker Container ..."
docker run --name "${name}-${ver}" \
-e "spring_cloud_zookeeper_enabled=${spring_cloud_zookeeper_enabled}" \
-e "spring_cloud_zookeeper_connectString=${spring_cloud_zookeeper_connectString}" \
-e "spring_cloud_zookeeper_auth_info=${spring_cloud_zookeeper_auth_info}" \
-e "spring_cloud_zookeeper_auth_secky=${spring_cloud_zookeeper_auth_secky}" \
-e "spring_profiles_active=${spring_profiles_active}" \
-d -p 21002:21002 --restart=always ${docker_prefix}/${tag}
