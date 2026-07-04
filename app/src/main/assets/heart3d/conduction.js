import * as THREE from 'three';

export class ConductionSystemRenderer {
    constructor(scene, camera, container) {
        this.scene = scene;
        this.camera = camera;
        this.container = container;

        this.nodes = [];
        this.pathMesh = null;
        this.pulseMesh = null;
        this.nodeMeshes = [];

        this.isPlaying = false;
        this.bpm = 75;
        this.startTime = 0;

        this.captionDiv = document.createElement('div');
        this.captionDiv.style.position = 'absolute';
        this.captionDiv.style.bottom = '20px';
        this.captionDiv.style.left = '50%';
        this.captionDiv.style.transform = 'translateX(-50%)';
        this.captionDiv.style.color = 'white';
        this.captionDiv.style.fontFamily = 'sans-serif';
        this.captionDiv.style.fontSize = '18px';
        this.captionDiv.style.textShadow = '0 0 5px black';
        this.captionDiv.style.pointerEvents = 'none';
        this.captionDiv.style.display = 'none';
        this.container.appendChild(this.captionDiv);

        this.initPulse();
    }

    initPulse() {
        const geometry = new THREE.SphereGeometry(0.02, 16, 16);
        const material = new THREE.MeshStandardMaterial({
            color: 0xffff00,
            emissive: 0xffff00,
            emissiveIntensity: 2
        });
        this.pulseMesh = new THREE.Mesh(geometry, material);
        this.pulseMesh.visible = false;
        this.scene.add(this.pulseMesh);
    }

    setPathway(nodes) {
        this.nodes = nodes;
        this.rebuildPath();
    }

    rebuildPath() {
        // Remove old meshes
        if (this.pathMesh) this.scene.remove(this.pathMesh);
        this.nodeMeshes.forEach(m => this.scene.remove(m));
        this.nodeMeshes = [];

        if (this.nodes.length < 2) return;

        const points = this.nodes.map(n => new THREE.Vector3(n.anchor[0], n.anchor[1], n.anchor[2]));

        // Create tube
        const curve = new THREE.CatmullRomCurve3(points);
        const tubeGeometry = new THREE.TubeGeometry(curve, 64, 0.005, 8, false);
        const tubeMaterial = new THREE.MeshStandardMaterial({ color: 0xffd700 });
        this.pathMesh = new THREE.Mesh(tubeGeometry, tubeMaterial);
        this.scene.add(this.pathMesh);

        // Create node spheres
        const nodeGeo = new THREE.SphereGeometry(0.01, 8, 8);
        const nodeMat = new THREE.MeshStandardMaterial({ color: 0xffd700 });
        this.nodes.forEach(n => {
            const mesh = new THREE.Mesh(nodeGeo, nodeMat);
            mesh.position.set(n.anchor[0], n.anchor[1], n.anchor[2]);
            this.scene.add(mesh);
            this.nodeMeshes.push(mesh);
        });
    }

    setPlaying(playing) {
        this.isPlaying = playing;
        this.pulseMesh.visible = playing;
        this.captionDiv.style.display = playing ? 'block' : 'none';
        if (playing) this.startTime = performance.now();
    }

    setBpm(bpm) {
        this.bpm = bpm;
    }

    update(time) {
        if (!this.isPlaying || this.nodes.length < 2) return;

        const cycleMs = 60000 / this.bpm;
        const elapsed = (time - this.startTime) % cycleMs;

        // Find current segment
        let currentIdx = -1;
        for (let i = 0; i < this.nodes.length - 1; i++) {
            if (elapsed >= this.nodes[i].arrivalMs && elapsed < this.nodes[i+1].arrivalMs) {
                currentIdx = i;
                break;
            }
        }

        if (currentIdx !== -1) {
            const nodeA = this.nodes[currentIdx];
            const nodeB = this.nodes[currentIdx + 1];
            const segmentElapsed = elapsed - nodeA.arrivalMs;
            const segmentDuration = nodeB.arrivalMs - nodeA.arrivalMs;
            const t = segmentElapsed / segmentDuration;

            this.pulseMesh.position.set(
                nodeA.anchor[0] + (nodeB.anchor[0] - nodeA.anchor[0]) * t,
                nodeA.anchor[1] + (nodeB.anchor[1] - nodeA.anchor[1]) * t,
                nodeA.anchor[2] + (nodeB.anchor[2] - nodeA.anchor[2]) * t
            );
            this.pulseMesh.visible = true;

            // Update caption - we'll need to pass locale or have labels in nodes
            const locale = window.currentLocale || 'en';
            const label = locale === 'ru' ? nodeB.labelRu : nodeB.labelEn;
            this.captionDiv.textContent = label;
        } else {
            // Diastole phase
            this.pulseMesh.visible = false;
            const locale = window.currentLocale || 'en';
            this.captionDiv.textContent = locale === 'ru' ? 'Диастола' : 'Diastole';
        }
    }
}
