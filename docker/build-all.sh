#!/bin/bash
oecho "This script is dead"
exit 1
function box_out() {
    echo "---------------"
    echo $*
    echo "---------------"
}

function showHelp {
	echo " ---- USAGE ----"
	echo $0 " Builds all docker projects. Nice if you want to be sure that dependent projects are up2date"
	echo $0 "--rm [--force|-f] remove and build all. Use the force if Docker complains that containers are using your images"
	echo Add in OPTS to control docker build, e.g. OPTS="--no-cache=true" ./build-all.sh	
} 

set -e

while [[ $# > 0 ]] ; do
	key="$1"
	
	case $key in 
		-f|--force)
	    FORCE=true
	    shift # past argument
	    ;;
		--rm|--remove)
	    REMOVE=true
	    shift # past argument
	    ;;
		-h|--help)
		showHelp
		exit 0
	    shift # past argument
	    ;;
	    *)
	    echo "Unknown option $2"
	    ;;
	esac
done	    

# The indents is a primitive way to keep track of dependencies, 
#   e.g. "dbc-glassfish-flowstore" depends on "dbc-glassfish" 
declare -a arr=("update-postgres" \
                "update-payara" \
                "update-payara-deployer")


for dockerfile in "${arr[@]}" ; do
	box_out Building $dockerfile	

	if [ "$REMOVE" = true ] ; then
		if [ "$FORCE" = true ] ; then
			RM_ARGS+=" --force"
		fi
		if [ "docker images -q | grep $dockerfile | wc -l" gt 0 ] ; then
			docker rmi $RM_ARGS $dockerfile
		fi 
		# Remove untagged images
		if [ "docker images -q --filter 'dangling=true' | wc -l'" gt 0 ] ; then
			docker rmi $(docker images -q --filter "dangling=true")
		fi
	fi
    docker build $OPTS -t $dockerfile $dockerfile
    BUILT+=$dockerfile,
done
echo "The following docker images have been built: " $BUILT
