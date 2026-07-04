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

        this.isXray = false;
        this.isCutaway = false;
        this.cutPosition = 0.5;
        this.clippingPlane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);

        this.isEditing = false;
        this.model = null;

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
        this.setupTouch();
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

    setModel(model) {
        this.model = model;
        this.applyClippingAndXray();
    }

    setPathway(nodes) {
        this.nodes = nodes;
        this.rebuildPath();
    }

    rebuildPath() {
        if (this.pathMesh) this.scene.remove(this.pathMesh);
        this.nodeMeshes.forEach(m => this.scene.remove(m));
        this.nodeMeshes = [];

        if (this.nodes.length < 2) return;

        const points = this.nodes.map(n => new THREE.Vector3(n.anchor[0], n.anchor[1], n.anchor[2]));
        const curve = new THREE.CatmullRomCurve3(points);
        const tubeGeometry = new THREE.TubeGeometry(curve, 64, 0.005, 8, false);
        const tubeMaterial = new THREE.MeshStandardMaterial({ color: 0xffd700 });
        this.pathMesh = new THREE.Mesh(tubeGeometry, tubeMaterial);
        this.scene.add(this.pathMesh);

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

    setXray(enabled) {
        this.isXray = enabled;
        this.applyClippingAndXray();
    }

    setCutaway(enabled) {
        this.isCutaway = enabled;
        this.applyClippingAndXray();
    }

    setCutPosition(pos) {
        this.cutPosition = pos;
        this.updateClippingPlane();
    }

    updateClippingPlane() {
        if (!this.model) return;
        const box = new THREE.Box3().setFromObject(this.model);
        const z = box.min.z + (box.max.z - box.min.z) * this.cutPosition;
        this.clippingPlane.constant = -z;
    }

    applyClippingAndXray() {
        if (!this.model) return;
        this.updateClippingPlane();

        const planes = this.isCutaway ? [this.clippingPlane] : [];

        this.model.traverse(child => {
            if (child.isMesh) {
                child.material.clippingPlanes = planes;
                child.material.transparent = this.isXray;
                child.material.opacity = this.isXray ? 0.28 : 1.0;
                child.material.side = THREE.DoubleSide; // To see interior when cut
            }
        });
    }

    setEditing(enabled) {
        this.isEditing = enabled;
        if (enabled) {
            this.nodes = [];
            this.rebuildPath();
            this.showHint("Tap to place SA node (1/7)");
        } else {
            this.hideHint();
        }
    }

    showHint(text) {
        if (!this.hintDiv) {
            this.hintDiv = document.createElement('div');
            this.hintDiv.style.position = 'absolute';
            this.hintDiv.style.top = '20px';
            this.hintDiv.style.left = '50%';
            this.hintDiv.style.transform = 'translateX(-50%)';
            this.hintDiv.style.backgroundColor = 'rgba(0,0,0,0.6)';
            this.hintDiv.style.color = 'white';
            this.hintDiv.style.padding = '5px 15px';
            this.hintDiv.style.borderRadius = '15px';
            this.hintDiv.style.fontFamily = 'sans-serif';
            this.container.appendChild(this.hintDiv);
        }
        this.hintDiv.textContent = text;
        this.hintDiv.style.display = 'block';
    }

    hideHint() {
        if (this.hintDiv) this.hintDiv.style.display = 'none';
    }

    setupTouch() {
        const raycaster = new THREE.Raycaster();
        const mouse = new THREE.Vector2();

        this.container.addEventListener('touchstart', (event) => {
            if (!this.isEditing || !this.model) return;

            const touch = event.touches[0];
            mouse.x = (touch.clientX / window.innerWidth) * 2 - 1;
            mouse.y = -(touch.clientY / window.innerHeight) * 2 + 1;

            raycaster.setFromCamera(mouse, this.camera);
            const intersects = raycaster.intersectObject(this.model, true);

            if (intersects.length > 0) {
                this.addNodeAt(intersects[0].point);
            }
        });
    }

    addNodeAt(point) {
        const template = window.conductionTemplate;
        if (this.nodes.length >= template.length) return;

        const nextTemplate = template[this.nodes.length];
        const newNode = {
            ...nextTemplate,
            anchor: [point.x, point.y, point.z]
        };
        this.nodes.push(newNode);
        this.rebuildPath();

        if (this.nodes.length < template.length) {
            this.showHint(`Tap to place ${template[this.nodes.length].labelEn} (${this.nodes.length + 1}/${template.length})`);
        } else {
            this.showHint("Pathway complete!");
            if (typeof Android !== 'undefined') Android.saveConduction(JSON.stringify(this.nodes));
        }
    }

    update(time) {
        if (!this.isPlaying || this.nodes.length < 2) return;

        const cycleMs = 60000 / this.bpm;
        const elapsed = (time - this.startTime) % cycleMs;

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

            const locale = window.currentLocale || 'en';
            const label = locale === 'ru' ? nodeB.labelRu : nodeB.labelEn;
            this.captionDiv.textContent = label;
        } else {
            this.pulseMesh.visible = false;
            const locale = window.currentLocale || 'en';
            this.captionDiv.textContent = locale === 'ru' ? 'Диастола' : 'Diastole';
        }
    }
}
